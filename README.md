# MCP Wrapped SDK for [Model Context Protocol (MCP) Kotlin-SDK](https://github.com/modelcontextprotocol/kotlin-sdk)

A robust and extensible [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server implementation for Android platforms. This library is a streamlined wrapper that empowers Android developers to quickly integrate and build MCP-compliant servers with dynamic tool management, real-time notifications, and native Android compatibility.

## Features

- **Transport Agnostic**: Supports multiple communication protocols including SSE and stdio
- **Dynamic Capability Management**: Runtime addition/removal of tools, resources, and prompts
- **Notification Broadcasting**: Efficiently propagates notifications to all connected clients
- **Dependency Injection**: Modular architecture with built-in dependency injection support

## Installation

Add the dependency to your `build.gradle` file:

```kotlin
implementation("io.github.nosaywanan:mcp-wrapper:0.7.2")
```

## Quick Start

### Basic Server Implementation

```kotlin
class MyMcpServer(): McpServer(name = "AppName", version = "1.0.0"){  
  
    @mcp.Tools(description = "this tools is used to get weather with city")  
    fun getWeather(city:String):String?{  
        notifier.message("this is message from app")  
        return "this is weather"  
    }  
    @mcp.Prompt(description = "this prompt is used to get weather function call prompt")  
    fun getWeatherPrompt():String{  
        return "this is prompt for getWeather"  
    }  
    @mcp.Prompt(description = "this prompt is used to get some resource")  
    fun getResource(name: String):String{  
        return "this is resource for ${name}"  
    }  
}
```

### Starting the Server

```kotlin
val server = MyMcpServer()

// Start with SSE transport on port 8080
server.sse(8080)

// Or start with HTTP transport (when implemented)
// server.http(8080)

// Stop the server when needed
server.stop()
```

## Transport Protocols

The MCP server supports multiple transport protocols:

### SSE (Server-Sent Events)
```kotlin
server.sse(8080)  // Starts SSE server on port 8080
```
Server will be available at: `http://0.0.0.0:8080/sse`

### Stdio (Standard Input/Output)
```kotlin
// Stdio transport coming soon
```

### HTTP (Planned)
```kotlin
// http-streamable transport don't support by offical mcp-sdk
```

## Advanced Usage

### Dependency Injection

```kotlin
class AdvanceMcpServer(): McpServer(name = "AppName", version = "1.0.0"), McpInjectHandler{  
    @mcp.Tools(description = "this tools is used to get weather with city")  
    fun getWeather(city:String):String?{  
        notifier.message("this is message from app")  
        return "this is weather"  
    }  
  
    override fun inject(function: KFunction<*>): Boolean {  
        if (function.name == "getWeather"){  
            return false  
        }  
        return true  
    }  
}
```


### Sending Notifications to MCP Client

The framework provides intelligent parameter injection. When the last parameter of a method is of type `Notifier`, the framework automatically injects a `DefaultNotifier` instance for application-level usage.

```kotlin
class MyMcpServer() : McpServer(name = "AppName", version = "1.0.0") {  
  
    @mcp.Tools(description = "This tool retrieves weather information for a specified city")  
    fun getWeather(city: String, notifier: Notifier): String? {  
        // Simply add a Notifier parameter to your function
        notifier.message("This is a message from the application")  
        return "Current weather information"  
    }  
}
```
## Debug Your MCP Server App
To streamline development and troubleshooting, you can use the `mcp-inspector.sh` script included in the demo to launch the official **MCP Inspector**—a web-based debugging tool provided by the Model Context Protocol team.  

For setup instructions and detailed usage, refer to the official repository:  
[@modelcontextprotocol/inspector](https://github.com/modelcontextprotocol/inspector)


## Contributing
We welcome contributions! Please see our [Contributing Guide](./CONTRIBUTING.md) for details.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

