package open.ai.mcp

import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import open.ai.mcp.compiler.mcp
import open.ai.mcp.core.McpInjectHandler
import open.ai.mcp.core.McpServer
import open.ai.mcp.core.Notifier
import open.ai.mcp.core.NotifierBuilder
import open.ai.mcp.core.impl.DefaultNotifier
import kotlin.reflect.KFunction

suspend fun  main(args: Array<String>) {
    val server = MyMcpServer { functionName ->
        println("Function called: $functionName")
    }
    server.start(port = 9999, wait = true)
}
class MyMcpServer(
    // Function to call when a function is called
    val functionCall: (String) -> Unit) : McpServer(name = "MyServer", version = "1.0"), McpInjectHandler, NotifierBuilder {

    @mcp.Tools(
        title = "天気を確認する",
        name = "getWeather",
        description = "Retrieves the current weather for a specified city."
    )
    fun getWeather(
        @mcp.Tools.Argument(
            description = "The name of the city to fetch weather for."
        )
        city: String,
    ): String? {
        functionCall.invoke("getWeather")
        runBlocking { delay(4000) }
        return buildJsonObject {
            put("city", city)
            put("weather", "Sunny")
            put("temperature", 25)
            put("humidity", 60)
        }.toString()
    }

    @mcp.Tools(
        description = "Asynchronously retrieves the weather for a city. " +
                "This method returns immediately and performs the operation in the background. " +
                "Once complete, the result is sent to the client via notifications."
    )
    fun getWeatherAsync(city: String, notifier: Notifier?): String {
        // Simulate processing
        (1 until 100).forEach { index ->
            // Progress notification
            notifier?.progress(index, total = 100, "Processing weather request for $city...")
        }

        val result = CoroutineScope(Dispatchers.Default).async {
            // Simulate blocking operation
            delay(2000)
            // Message notification
            notifier?.message("Weather data for $city is ready.")
        }
        // Return result immediately,don't wait for result
        return "Weather request initiated for $city"
    }

    private val imSupportAsyncWeather = true

    /**
     * Determines whether a function should be injected with dependencies.
     *
     * This method provides conditional dependency injection control:
     * - For the [getWeatherAsync] function, injection is controlled by the [imSupportAsyncWeather] flag
     * - For all other functions, injection is enabled by default
     *
     * @param function The function to check for injection requirements
     * @return true if the function should be injected, false otherwise
     */
    override fun inject(function: KFunction<*>): Boolean {
        if (function == this::getWeatherAsync) {
            return imSupportAsyncWeather
        }
        return true
    }
    /**
     * Builds a notifier for the open.ai.mcp.MyMcpServer class.
     *
     * This method creates a new instance of the MyNotifier class with the provided token.
     *
     * @param token The token used for authentication
     * @return A new instance of the MyNotifier class
     */
    override fun buildNotifier(token: String): Notifier? {
        return MyNotifier(token)
    }
    /**
     * A custom notifier implementation that extends the DefaultNotifier class.
     *
     * This class provides custom notification functionality for the open.ai.mcp.MyMcpServer class.
     * It extends the DefaultNotifier class and overrides its methods to provide custom notification behavior.
     *
     * @param token The token used for authentication
     */
    inner class MyNotifier(token: String) : DefaultNotifier(clientToken = token) {
        fun error(error:String) = message(
            buildJsonObject {
                put("error", error)
            }, LoggingLevel.error
        )
    }
}
