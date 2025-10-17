package open.ai.mcp.core.impl

import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.RequestId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import open.ai.mcp.core.GlobalNotificationHandler
import open.ai.mcp.core.Notifier

/**
 * Default implementation of the [Notifier] interface.
 *
 * This class is responsible for sending various types of notifications to the client,
 * such as progress updates and logging messages. It uses the Model Context Protocol (MCP)
 * to communicate with the client.
 *
 * @param clientToken A unique token identifying the client session. This token is used
 *                    to associate progress notifications with the correct client.
 */
open class DefaultNotifier(val clientToken: String) : Notifier {
    /**
     * Sends a progress notification to the client.
     *
     * @param progress The current progress value.
     * @param total The total progress value.
     * @param message The progress message, which can be null.
     */
    override fun progress(progress: Double, total: Double, message: String?) = sendNotification(
        ProgressNotification(
            ProgressNotification.Params(
                progress = progress.toDouble(),
                progressToken = RequestId.StringId(clientToken),
                total = total.toDouble(),
                message = message,
                _meta = buildJsonObject {
                    put("progressToken", clientToken)
                },
            )
        ).apply {
            if (clientToken.isNullOrEmpty()) {
                throw IllegalArgumentException("mcp client don't supply  progressToken for this session")
            }
        }
    )
}