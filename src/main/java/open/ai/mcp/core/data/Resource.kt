package open.ai.mcp.core.data

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.Resource
import io.modelcontextprotocol.kotlin.sdk.ResourceContents
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredResource
import java.lang.reflect.Method
import kotlin.reflect.KFunction
/**
 * Represents a request for an MCP (Model Context Protocol) resource.
 * 
 * This class encapsulates the metadata and invocation details needed to create a registered MCP resource.
 * It handles the conversion of method invocation results into appropriate MCP resource content formats.
 * 
 * @property name The display name of the resource
 * @property description A human-readable description of the resource's purpose and content
 * @property uri The unique identifier for accessing this resource
 * @property mimeType The media type of the resource content (optional)
 * @property method The Java reflection Method object to invoke for retrieving resource data
 * @property function The Kotlin function reference corresponding to the method
 * @property obj The target object instance on which to invoke the method
 */
data class McpResourceRequest(val name:String,
                              val description:String,
                              val uri:String,
                              val mimeType:String?,
                              val method: Method,
                              val function: KFunction<*>,val obj:Any) {
    /**
     * Constructs a [RegisteredResource] instance based on the metadata provided in this request.
     *
     * This method registers the resource with the MCP server and defines the behavior for handling
     * read requests. When a [ReadResourceRequest] is received, it invokes the specified method
     * on the target object and processes the result into a [ReadResourceResult].
     *
     * @return A [RegisteredResource] that can be used by an MCP server to serve resource content.
     */

    fun createRegisteredResource(): RegisteredResource{
        val resource = Resource(name = name, description = description, uri = uri, mimeType = mimeType)
        return RegisteredResource(resource){ request ->
            buildResourceResult(request)
        }
    }
    
    /**
     * Builds a [ReadResourceResult] by invoking the encapsulated method and processing its result.
     *
     * This method handles three types of return values from the invoked method:
     * 1. If the result is already a [ReadResourceResult], it is returned directly.
     * 2. If the result is a [List], each element is converted into a [ResourceContents] and wrapped in a [ReadResourceResult].
     * 3. For all other types, the result is converted to a [TextResourceContents] and returned as a single-element list
     *    within a [ReadResourceResult].
     *
     * @param readResourceRequest The incoming request that triggered this resource read operation.
     * @return A [ReadResourceResult] containing the processed content based on the method's return value.
     *
     * @throws IllegalAccessException if the method is inaccessible.
     * @throws IllegalArgumentException if the method arguments do not match.
     * @throws InvocationTargetException if the method invocation throws an exception.
     */
    private fun buildResourceResult(readResourceRequest: ReadResourceRequest): ReadResourceResult{
        val result = method.invoke(obj)
        if (result is ReadResourceResult){
            return result
        }
        if (result is List<*>){
            return ReadResourceResult(contents = result.map { createResourceContents(it) })
        }
        return ReadResourceResult(
            contents = arrayListOf(createResourceContents(result)),
        )
    }
    
    /**
     * Converts a method invocation result into a [ResourceContents] instance.
     *
     * This helper method attempts to cast the given result to a [ResourceContents]. If the cast fails,
     * it wraps the string representation of the result in a [TextResourceContents], using the resource's
     * URI and MIME type for proper content identification.
     *
     * @param result The object returned from the method invocation, which may be null.
     * @return A [ResourceContents] representing the data to be included in the resource response.
     */
    private fun createResourceContents(result: Any?):ResourceContents{
        return result as? ResourceContents ?: TextResourceContents(text = result?.toString()?:"null", uri = uri, mimeType = mimeType)
    }
}