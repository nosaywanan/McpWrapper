package open.ai.mcp.core



import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.EmptyRequestResult
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.Request
import io.modelcontextprotocol.kotlin.sdk.RequestResult
import io.modelcontextprotocol.kotlin.sdk.ToolListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.shared.RequestHandlerExtra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import open.ai.mcp.core.data.McpPromptRequest
import open.ai.mcp.core.data.McpResourceRequest
import open.ai.mcp.core.data.McpToolsRequest

/**
 * Main entry point for the Model Context Protocol (MCP) server implementation.
 *
 * This class provides a robust and extensible foundation for building MCP-compliant servers
 * on Android platforms. It abstracts the underlying transport mechanisms and offers
 * high-level APIs for managing tools, resources, and prompts dynamically.
 *
 * Features:
 * - Transport Agnostic: Supports multiple communication protocols such as SSE and stdio.
 * - Dynamic Capability Management: Allows runtime addition/removal of tools, resources, and prompts.
 * - Notification Broadcasting: Efficiently propagates notifications to all connected clients.
 * - Android Integration: Seamlessly integrates with Android's logging and concurrency frameworks.
 *
 * Usage:
 * Extend this abstract class and implement the required abstract methods. Then, instantiate
 * your implementation and call [start] with the desired transport and port configuration.
 *
 * Example:
 *  * class MyMcpServer : McpServer("MyServer", "1.0.0")
 *
 * val server = MyMcpServer()
 * server.start(8080, transport = Transport.sse)
 *  *
 * @param name The human-readable name of the server.
 * @param version The version string of the server implementation.
 */
interface McpServerProxy {
    val name: String
    fun addTools(requests: List<McpToolsRequest>)
    fun removeTools(requests: List<McpToolsRequest>)

    fun addResources(requests: List<McpResourceRequest>)
    fun removeResources(requests: List<McpResourceRequest>)

    fun addPrompts(requests: List<McpPromptRequest>)
    fun removePrompts(requests: List<McpPromptRequest>)

    fun sendNotification(notification: Notification)
}

enum class Transport {
    sse, stdio, https
}

/**
 * Enumeration representing the supported transport protocols for the MCP server.
 *
 * - [sse]: Server-Sent Events transport.
 * - [stdio]: Standard input/output transport.
 * - [https]: Secure HTTP transport (currently not implemented).
 */
abstract class McpServer constructor(override val name: String, private val version: String) :
    McpServerProxy, GlobalNotificationHandler.Observer {
    /**
     * The core MCP server instance responsible for handling protocol-level communication.
     *
     * This instance encapsulates the logic for managing tools, resources, prompts,
     * and notifications as defined by the Model Context Protocol specification.
     * It serves as the primary interface between the server implementation and the
     * underlying transport layer (e.g., SSE, stdio).
     */
    val server = Server(
        Implementation(name = name, version = version),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        )
    )

    /**
     * Dependency injector instance used to manage and inject dependencies
     * into the server components. This facilitates modular and testable code
     * by allowing loose coupling between different parts of the system.
     *
     * The injector is initialized with the current server instance, enabling
     * automatic injection of required services and utilities.
     */
    val injector = McpInjector(this)

    fun uninject() {
        injector.uninject(this)
        sendNotification(ToolListChangedNotification())
    }

    override fun addPrompts(requests: List<McpPromptRequest>) {
        server.addPrompts(requests.map { it.createRegisteredPrompt() })
    }

    /**
     * Removes the injected dependencies from the server instance and notifies
     * all connected clients about the change by sending a [ToolListChangedNotification].
     *
     * This method should be called when the server is being shut down or reconfigured
     * to ensure that all dependencies are properly cleaned up and clients are informed
     * of the tool list update.
     */
    override fun removePrompts(requests: List<McpPromptRequest>) {
        server.removePrompts(requests.map { it.name })
    }

    override fun addTools(requests: List<McpToolsRequest>) {
        server.addTools(requests.map { it.createRegisteredTool() })
    }

    override fun removeTools(requests: List<McpToolsRequest>) {
        server.removeTools(requests.map { it.name })
    }

    override fun addResources(requests: List<McpResourceRequest>) {
        server.addResources(requests.map { it.createRegisteredResource() })
    }

    override fun removeResources(requests: List<McpResourceRequest>) {
        server.removeResources(requests.map { it.name })
    }


    override fun onNotification(notification: Notification) {
        sendNotification(notification)
    }

    /**
     * Handles incoming notifications from the MCP client and propagates them to
     * the registered observers. This method acts as a bridge between the protocol
     * layer and the application-specific notification handling logic.
     *
     * @param notification The notification object received from the client.
     * @see GlobalNotificationHandler
     * @see Notification
     */
    override fun sendNotification(notification: Notification) {
        CoroutineScope(Dispatchers.Default).launch {
            server.notification(notification)
        }
    }

    suspend fun sse(port: Int, host: String = "0.0.0.0") {
        start(port, host = host, wait = true, Transport.sse)
    }

    suspend fun http(port: Int, host: String = "0.0.0.0") {
        start(port, host = host, wait = true, Transport.https)
    }

    suspend fun start(
        port: Int,
        host: String = "0.0.0.0",
        wait: Boolean = true,
        transport: Transport = Transport.sse
    ) {
        injector.inject(this@McpServer)
        configServerBeforeLaunch()
        when (transport) {
            Transport.sse -> sse(host, port, wait)
            Transport.https -> https(host, port, wait)
            else -> throw IllegalArgumentException("the mcp server not support ${transport.name}")
        }
    }

    suspend fun stop(){
        GlobalNotificationHandler.unregister(this@McpServer)
        injector.uninject(this)
        server.close()
    }

    /**
     * Creates and returns an instance of the application engine factory.
     *
     * @return ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration> The application engine factory instance
     *         used to create and configure the application engine. Defaults to returning the CIO engine factory.
     */
    protected open fun applicationEngineFactory(): ApplicationEngineFactory<ApplicationEngine, out ApplicationEngine.Configuration> =
        CIO

    /**
     * Configures the server with essential handlers and observers before launching.
     *
     * This method sets up the global notification handler to observe and propagate
     * notifications, as well as registers core request handlers such as
     * [LoggingMessageNotification.SetLevelRequest] to integrate with Android's logging system.
     *
     * It is automatically invoked during the server startup sequence and should not
     * be called manually.
     */
    private fun configServerBeforeLaunch() {
        GlobalNotificationHandler.register(this)
        server.setRequestHandler<LoggingMessageNotification.SetLevelRequest>(Method.Defined.LoggingSetLevel) { request, extra ->
            EmptyRequestResult(_meta = buildJsonObject { put("logger", "Android Logcat") })
        }
    }

    private fun sse(host: String, port: Int, wait: Boolean) =
        embeddedServer(applicationEngineFactory(), host = host, port = port) { mcp { return@mcp server } }
            .also { println("McpServer[${name}] running at http://${host}:${port}/sse") }
            .start(wait = wait)

    private fun https(host: String, port: Int, wait: Boolean) {

        throw UnsupportedOperationException("the mcp sdk doesn't support http-streamable at now")
    }
}


