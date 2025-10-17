package open.ai.mcp.core

import open.ai.mcp.compiler.mcp
import open.ai.mcp.core.data.McpPromptRequest
import open.ai.mcp.core.data.McpResourceRequest
import open.ai.mcp.core.data.McpToolsRequest
import open.ai.mcp.core.impl.DefaultNotifier
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
/**
 * Interface for handling MCP injection filtering.
 * Implementations can control which functions are injected based on custom logic.
 */
interface McpInjectHandler{
    /**
     * Determines whether a given function should be injected into the MCP server.
     *
     * This method allows implementing classes to apply custom filtering logic to control
     * which functions are exposed through the MCP server. The default implementation
     * returns true for all functions, but implementations can override this to provide
     * more selective injection based on function metadata, annotations, or other criteria.
     *
     * @param function The Kotlin function being considered for injection
     * @return true if the function should be injected, false otherwise
     */
    fun inject(function: KFunction<*>): Boolean
}

/**
 * Manages the injection and un-injection of MCP (Model Communication Protocol) annotated methods
 * from objects into an [McpServerProxy]. This class handles three types of MCP annotations:
 * - [mcp.Tools]: for tool registration
 * - [mcp.Prompt]: for prompt registration
 * - [mcp.Resource]: for resource registration
 *
 * It supports filtering of functions to be injected via the [McpInjectHandler] interface,
 * allowing custom logic to determine which methods should be exposed through the MCP server.
 *
 * @param server The [McpServerProxy] instance to which annotated methods will be registered or unregistered.
 */
class McpInjector(val server: McpServerProxy) {
    private val serverName = server.name
    
    
    fun inject(obj: Any){
        collectMcpAnnotations(obj,obj as? McpInjectHandler,true)
    }
    
    fun uninject(obj: Any){
        collectMcpAnnotations(obj,obj as? McpInjectHandler,false)
    }
    /**
     * Collects MCP annotations from the given object and processes them based on the registration flag.
     * If [register] is true, it injects the annotated methods; otherwise, it un-injects them.
     *
     * @param any The object from which MCP annotations are collected.
     * @param handler An optional [McpInjectHandler] to filter which functions should be processed.
     * @param register A flag indicating whether to register (inject) or unregister (un-inject) the methods.
     */
    private fun collectMcpAnnotations(any: Any,handler: McpInjectHandler?,register: Boolean){
        //获取注入类的全部成员method
        any::class.memberFunctions.forEach { func ->
            if (register){
                val injectFunction = handler?.inject(func) ?: true
                if (injectFunction){
                    injectMcpFunctionAnnotations(func,any)
                }else{
                    println("[McpInjector]The method ${func.name} was filter by [McpInjectHandler]")
                }
            }else{
                uninjectMcpFunctionAnnotations(func,any)
            }
        }
    }
    /**
     * Processes the MCP annotations of a given function for injection or un-injection.
     *
     * This method inspects the annotations on a Kotlin reflective [KFunction] and creates
     * corresponding request objects for tools, prompts, and resources. These are then
     * registered or unregistered with the associated [McpServerProxy].
     *
     * @param function The Kotlin reflective function to process.
     * @param any The instance of the class that contains the function.
     */

    private fun injectMcpFunctionAnnotations(function: KFunction<*>,any: Any){
        //这里获取javaMethod 主要是为了 反射执行方法时的方便，同时由于mcp 注解时java实现的，所以使用javaMethod 更方便获取注解
        val method = function.javaMethod!!
        val _toolsRequestList: MutableList<McpToolsRequest> = ArrayList()
        val _promptRequestList: MutableList<McpPromptRequest> = ArrayList()
        val _resourceRequestList: MutableList<McpResourceRequest> = ArrayList()

        //获取方法的全部注解
        method.annotations.forEach {anno->
            //TOOLS
            if (anno is mcp.Tools){
                val request = createMcpToolRequest(anno,method,function = function,any)
                _toolsRequestList.add(request)
            }
            //PROMPT
            if (anno is mcp.Prompt){
                val request = createMcpPromptRequest(anno,method,function = function,any)
                _promptRequestList.add(request)
            }
            //RESOURCE
            if (anno is mcp.Resource){
                val request = createMcpResourceRequest(anno,method,function,any)
                _resourceRequestList.add(request)
            }
        }
        server.addTools(_toolsRequestList)
        server.addPrompts(_promptRequestList)
        server.addResources(_resourceRequestList)
    }
    
    /**
     * Processes the MCP annotations of a given function for un-injection.
     *
     * This method inspects the annotations on a Kotlin reflective [KFunction] and creates
     * corresponding request objects for tools, prompts, and resources. These are then
     * unregistered from the associated [McpServerProxy].
     *
     * @param function The Kotlin reflective function to process.
     * @param any The instance of the class that contains the function.
     */

    private fun uninjectMcpFunctionAnnotations(function: KFunction<*>,any: Any){
        //这里获取javaMethod 主要是为了 反射执行方法时的方便，同时由于mcp 注解时java实现的，所以使用javaMethod 更方便获取注解
        val method = function.javaMethod!!
        val _toolsRequestList: MutableList<McpToolsRequest> = ArrayList()
        val _promptRequestList: MutableList<McpPromptRequest> = ArrayList()
        val _resourceRequestList: MutableList<McpResourceRequest> = ArrayList()

        //获取方法的全部注解
        method.annotations.forEach {anno->
            //TOOLS
            if (anno is mcp.Tools){
                val request = createMcpToolRequest(anno,method,function = function,any)
                _toolsRequestList.add(request)
            }
            //PROMPT
            if (anno is mcp.Prompt){
                val request = createMcpPromptRequest(anno,method,function = function,any)
                _promptRequestList.add(request)
            }
            //RESOURCE
            if (anno is mcp.Resource){
                val request = createMcpResourceRequest(anno,method,function,any)
                _resourceRequestList.add(request)
            }
        }
        server.removeTools(_toolsRequestList)
        server.removePrompts(_promptRequestList)
        server.removeResources(_resourceRequestList)
    }
    /**
     * Creates an [McpResourceRequest] object based on the provided [mcp.Resource] annotation,
     * associated [Method], [KFunction], and target object instance.
     *
     * This method prioritizes the name and description specified in the annotation. If either
     * is missing, it falls back to using the method name prefixed with the server name for the
     * resource name, and the resolved name as the description.
     *
     * @param anno The [mcp.Resource] annotation containing metadata for the resource.
     * @param method The Java [Method] instance corresponding to the Kotlin [KFunction].
     * @param function The Kotlin reflective [KFunction] representing the annotated method.
     * @param obj The instance of the class that contains the annotated method.
     * @return A fully constructed [McpResourceRequest] ready for registration or unregistration.
     */

    private fun createMcpResourceRequest(
        anno: mcp.Resource,
        method: Method,
        function: KFunction<*>,
        obj: Any
    ) : McpResourceRequest{
        var name = anno.name
        if (name.isNullOrEmpty()){
            name = "${serverName}_"+method.name
        }
        var desc = anno.description
        if (desc.isNullOrEmpty()){
            desc = name
        }
        return McpResourceRequest(name = name,
            uri = anno.uri, mimeType = anno.mimeType,
            description = desc, method = method,
            function = function, obj = obj)
    }
    /**
     * Creates an [McpToolsRequest] object based on the provided [mcp.Tools] annotation,
     * associated [Method], [KFunction], and target object instance.
     *
     * This method prioritizes the name and description specified in the annotation. If either
     * is missing, it falls back to using the method name prefixed with the server name for the
     * tool name, and the resolved name as the description. It also supports optional notifier
     * construction via the [NotifierBuilder] interface.
     *
     * @param tools The [mcp.Tools] annotation containing metadata for the tool.
     * @param method The Java [Method] instance corresponding to the Kotlin [KFunction].
     * @param function The Kotlin reflective [KFunction] representing the annotated method.
     * @param obj The instance of the class that contains the annotated method.
     * @return A fully constructed [McpToolsRequest] ready for registration or unregistration.
     */

    private fun createMcpToolRequest(tools: mcp.Tools,method: Method,function: KFunction<*>,obj:Any):McpToolsRequest{
        var name = tools.name
        if (name.isNullOrEmpty()){
            name = "${serverName}_" + method.name
        }
        var title = tools.title
        if (title.isNullOrEmpty()){
            title = name
        }
        var desc = tools.description
        if (desc.isNullOrEmpty()){
            desc = name
        }
        return McpToolsRequest(name = name, title = title,description = desc, function = function, obj = obj){ token->
            (obj as? NotifierBuilder)?.buildNotifier(token)
        }
    }
    /**
     * Creates an [McpPromptRequest] object based on the provided [mcp.Prompt] annotation,
     * associated [Method], [KFunction], and target object instance.
     *
     * This method prioritizes the name and description specified in the annotation. If either
     * is missing, it falls back to using the method name prefixed with the server name for the
     * prompt name, and the resolved name as the description.
     *
     * @param prompt The [mcp.Prompt] annotation containing metadata for the prompt.
     * @param method The Java [Method] instance corresponding to the Kotlin [KFunction].
     * @param function The Kotlin reflective [KFunction] representing the annotated method.
     * @param obj The instance of the class that contains the annotated method.
     * @return A fully constructed [McpPromptRequest] ready for registration or unregistration.
     */
    private fun createMcpPromptRequest(prompt: mcp.Prompt,method: Method,function: KFunction<*>,obj:Any): McpPromptRequest{
        var name = prompt.name
        if (name.isNullOrEmpty()){
            name = "${serverName}_" +method.name
        }
        var desc = prompt.description
        if (desc.isNullOrEmpty()){
            desc = name
        }
        return McpPromptRequest(name = name,description = desc, function = function,obj = obj)
    }
}