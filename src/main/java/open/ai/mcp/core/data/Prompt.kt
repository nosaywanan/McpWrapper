package open.ai.mcp.core.data


import io.modelcontextprotocol.kotlin.sdk.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Prompt
import io.modelcontextprotocol.kotlin.sdk.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredPrompt
import open.ai.mcp.compiler.mcp
import open.ai.mcp.core.createMultimodal
import java.lang.reflect.InvocationTargetException

import kotlin.collections.get
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaMethod

/**
 * Represents a request for generating MCP (Model Context Protocol) prompts.
 *
 * This class encapsulates the metadata and function required to create a registered prompt
 * that can be used by the MCP SDK. It handles argument extraction, prompt construction,
 * and result processing for prompt-based operations.
 *
 * @property name The unique identifier for the prompt
 * @property description Human-readable description of the prompt's purpose
 * @property function The Kotlin function that will be invoked when the prompt is executed
 * @property obj The object instance on which the function will be invoked (for member functions)
 */
data class McpPromptRequest(val name:String,val description:String, val function: KFunction<*>,val obj:Any) {

    /**
     * Creates a registered prompt from the MCP prompt request.
     *
     * This method constructs a [RegisteredPrompt] instance by building the prompt definition
     * with the provided name, description, and arguments. The prompt execution handler
     * delegates to [buildPromptResult] for processing incoming requests.
     *
     * @return A [RegisteredPrompt] instance ready for registration with the MCP SDK
     */
    fun createRegisteredPrompt(): RegisteredPrompt {
        val prompt = Prompt(name = name, description = description, arguments = buildPromptArguments())
        return RegisteredPrompt(prompt = prompt){ request ->
            buildPromptResult(this,request)
        }
    }

    private fun buildPromptArguments(): List<PromptArgument>{
        return function.parameters.map { createPromptArgumentByParameter(function,it) }.filter { it!=null }.map { it!! }
    }
    
    /**
     * Creates a prompt argument definition based on a function parameter's metadata.
     *
     * This method extracts parameter information including name, description, and required status
     * from the function parameter's annotations and metadata. It handles the @mcp.Prompt.Argument
     * annotation to customize the argument definition for MCP prompt generation.
     *
     * @param function The Kotlin function containing the parameter
     * @param parameter The specific parameter to analyze for argument creation
     * @return A PromptArgument instance if the parameter is valid, null if the parameter should be excluded
     */
    private fun createPromptArgumentByParameter(function:KFunction<*>,parameter: KParameter): PromptArgument?{
        println("_createPromptArgumentByParameter for ${function.name},${parameter.name}")

        parameter.name?:return null

        var name: String? = ""

        var desc = name

        var required = true
        val method = function.javaMethod!!

        parameter.annotations.forEach { annotation ->
            if (annotation is mcp.Prompt.Argument){
                desc = annotation.description
                required = annotation.required
            }
        }
        if (name.isNullOrEmpty()){
            name = parameter.name
        }
        if (desc.isNullOrEmpty()){
            desc = name
        }
        if (name.isNullOrEmpty()){
            name = method.name
        }
        if (desc.isNullOrEmpty()){
            desc = name
        }
        println("_createPromptArgumentByParameter ${name} ${desc} ${required}")
        return PromptArgument(name,desc,required)
    }

    /**
     * Processes a GetPromptRequest and executes the associated function to generate a prompt result.
     *
     * This method handles the core execution flow of an MCP prompt request by:
     * 1. Extracting arguments from the incoming GetPromptRequest
     * 2. Mapping argument values to function parameters by name
     * 3. Invoking the target function reflectively with the extracted parameters
     * 4. Handling invocation exceptions and converting the result to a standardized format
     *
     * The method ensures type safety by filtering out null parameter values and properly
     * handling Java reflection exceptions through try-catch blocks.
     *
     * @param request The McpPromptRequest containing function metadata and target object
     * @param getPromptRequest The incoming request containing argument values for prompt generation
     * @return GetPromptResult containing the processed prompt response or error handling
     * @throws RuntimeException if reflective invocation fails due to underlying function exceptions
     */
    private fun buildPromptResult(request: McpPromptRequest,getPromptRequest: GetPromptRequest): GetPromptResult{
        val args = getPromptRequest.arguments;
        val paramsValues = request.function.parameters.map { p -> args?.get(p.name) }.filter { it != null }.toTypedArray()
        val result = try {
            function.javaMethod!!.invoke(obj,*paramsValues)
        }catch (e: InvocationTargetException){
            e.printStackTrace()
            null
        }
        return createPromptResult(result)
    }
    
    /**
     * Converts the raw function execution result into a standardized GetPromptResult.
     *
     * This method handles various result types and transforms them into the appropriate
     * MCP SDK response format. It supports:
     * - Null results: Returns a default error message prompt
     * - GetPromptResult instances: Returns the result directly
     * - Collections: Maps each element to a PromptMessage
     * - Single objects: Wraps the object in a single PromptMessage
     *
     * The transformation ensures compatibility with the MCP protocol by properly
     * handling different return types from prompt execution functions.
     *
     * @param result The raw result object from function invocation
     * @return A standardized GetPromptResult suitable for MCP protocol responses
     */
    private fun createPromptResult(result: Any?): GetPromptResult {

        result ?: return GetPromptResult(
            description = "no result",
            messages = listOf(PromptMessage(Role.assistant, TextContent("方法没有返回参数")))
        )
        return result as? GetPromptResult
            ?: if (result is List<*>) {
                GetPromptResult(description = "", messages = result.map { createPromptMessage(it) })
            } else {
                GetPromptResult(description = "", messages = arrayListOf(createPromptMessage(result)))
            }
    }

    private fun createPromptMessage(result: Any?): PromptMessage {
        result ?: return PromptMessage(role = Role.assistant, content = TextContent(null))
        return result as? PromptMessage ?: PromptMessage(role = Role.assistant, content = result.createMultimodal())
    }
}