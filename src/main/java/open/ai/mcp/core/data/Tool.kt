package open.ai.mcp.core.data

import open.ai.mcp.compiler.mcp
import open.ai.mcp.core.createMultimodal
import open.ai.mcp.core.isHiddenParameter
import open.ai.mcp.core.mcpType
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import open.ai.mcp.core.Notifier
import open.ai.mcp.core.impl.DefaultNotifier
import open.ai.mcp.core.toJsonValue
import kotlin.collections.get
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.javaMethod

/**
 * Represents a request for an MCP tool, encapsulating the tool's metadata, execution logic,
 * and parameter handling. This class is responsible for creating a registered tool that can
 * be invoked by the MCP server with dynamic parameters provided at runtime.
 *
 * @property name The name of the tool. Used to identify the tool when invoked by the MCP client.
 * @property description A human-readable description of what the tool does.
 * @property function The Kotlin reflection reference to the actual method implementing the tool logic.
 * @property obj The instance on which the method should be invoked.
 * @property notifierBuilder A factory function used to create a notifier for progress updates during tool execution.
 */
data class McpToolsRequest(val name:String,
                           val title: String,
                           val description:String,
                           val function: KFunction<*>,
                           val obj:Any,
                           val notifierBuilder:((String)-> Notifier?)?) {
    /**
     * Constructs a [RegisteredTool] instance that encapsulates the tool's metadata and execution logic.
     * This method prepares the tool for registration with the MCP server, defining its input schema
     * and behavior when invoked. It handles parameter mapping, method invocation via reflection,
     * and result conversion to the expected MCP response format.
     *
     * The tool's input schema is dynamically generated based on the method's parameters and their annotations.
     * Parameters annotated with [mcp.Tools.Argument] contribute to the schema, with optional descriptions
     * and required flags.
     *
     * During tool invocation:
     * 1. Parameters are extracted from the [CallToolRequest] and mapped to the method's expected arguments.
     * 2. Special handling is applied for [Notifier] parameters, which are instantiated using the provided
     *    [notifierBuilder] if available, or a default implementation otherwise.
     * 3. The method is invoked using reflection, and the result is processed into a [CallToolResult].
     *
     * @return A [RegisteredTool] instance ready to be registered with the MCP server.
     * @see Tool
     * @see CallToolRequest
     * @see CallToolResult
     */
    fun createRegisteredTool(): RegisteredTool{
        val request = this
        //创建tool
        val tool = Tool(
            title = request.title,
            name = request.name,
            description = request.description,
            inputSchema = createToolInput(),
            //todo 
            outputSchema = null,
            //todo 
            annotations = null,
        )

        println("[RegisteredTool]")
        println("Name:\t${request.name}")
        println("Title:\t${request.title}")
        println("Description:\t${request.description}")
        println("----------------------------------------------------------------------------------------------------")

        return RegisteredTool(tool = tool){ callToolRequest ->

            val params = buildMethodParameterValues(request, callToolRequest).toMutableList()
            if (params.firstOrNull() == null){
                params.removeAt(0)
            }
            val parameters = params.toTypedArray()
            println("[CallToolRequest] request parameters: ${parameters.contentToString()}")
            val result = try {
                request.function.javaMethod!!.invoke(request.obj, *parameters)
            }catch (e: Exception){
                e.printStackTrace()
                e.message
            }
            println("[CallToolRequest] result: ${result}")
            createToolResult(result)
        }
    }

    private fun createToolResult(result: Any?): CallToolResult {
        result?:return CallToolResult(content = listOf(TextContent("invoke method error")))

        return result as? CallToolResult
            ?: if (result is List<*>) {
                CallToolResult(content = result.map { it?.createMultimodal() }.filter { it != null }.map { it!! })
            } else {
                CallToolResult(content = listOf(result.createMultimodal()))
            }
    }

    /**
     * Generates the input schema for the tool based on the method's parameters.
     * This schema defines the expected structure of the input arguments when the tool is invoked.
     * Each parameter is represented as a property in a JSON object, including its type and description.
     * Parameters annotated with [mcp.Tools.Argument] contribute to this schema.
     *
     * The schema also identifies which parameters are required, based on the [mcp.Tools.Argument.required] flag.
     * Hidden parameters (determined by [isHiddenParameter]) are excluded from the schema.
     *
     * @return A [Tool.Input] instance representing the tool's input schema.
     * @see Tool.Input
     * @see mcp.Tools.Argument
     */
    private fun createToolInput(): Tool.Input{
        //存放这个方法哪些参数是必须在mcp 执行时传入的
        val required = mutableListOf<String>()
        val parameters = buildJsonObject {
            function.parameters.forEach { parameter->
                if (!parameter.name.isNullOrEmpty() && !parameter.isHiddenParameter()){

                    val annotation = parameter.findAnnotations(mcp.Tools.Argument::class)
                    val argument = annotation.firstOrNull()
                    if ((argument?.required == true)){
                        required.add(parameter.name!!)
                    }
                    put(parameter.name!!,buildJsonObject {
                        put("description",argument?.description?:"")
                        put("type",parameter.type.mcpType())
                    })
                }
            }
        }
        return Tool.Input(
            properties = parameters,
            required = required
        )
    }

    /**
     * Constructs the input schema for the tool by inspecting the method's parameters.
     * This schema defines the expected structure of the input arguments when the tool is invoked.
     * Each parameter is represented as a property in a JSON object, including its type and description.
     * Only parameters annotated with [mcp.Tools.Argument] contribute to this schema.
     *
     * The schema also identifies which parameters are required based on the [mcp.Tools.Argument.required] flag.
     * Hidden parameters (determined by [isHiddenParameter]) are excluded from the schema.
     *
     * @return A [Tool.Input] instance representing the tool's input schema.
     * @see Tool.Input
     * @see mcp.Tools.Argument
     */
    private fun buildMethodParameterValues(request: McpToolsRequest,callToolRequest: CallToolRequest): List<Any?> {
        val args = callToolRequest.arguments.jsonObject.toMap()
        val progressToken = callToolRequest._meta.toMap().getOrDefault("progressToken","").toString()
        println("mcp client request parameters: ${args}，with progressToken: $progressToken")
        println("mcp server tool parameters: ${request.function.parameters.map { it.name }.toTypedArray().contentToString()}")
        return request.function.parameters.map { p ->
            if (p.type.classifier == Notifier::class){
                notifierBuilder?.invoke(progressToken)?: DefaultNotifier(progressToken)
            }else{
                args[p.name]?.toJsonValue()?.toString()
            }
        }
    }
}



