package open.ai.mcp.core

import io.modelcontextprotocol.kotlin.sdk.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.Notification
import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.ProgressToken
import io.modelcontextprotocol.kotlin.sdk.RequestId
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import open.ai.mcp.core.impl.DefaultNotifier


interface NotifierBuilder {
    fun buildNotifier(token: String): Notifier? = DefaultNotifier(token)
}


/**
 * Notifier is a core interface for sending various types of notifications within the system.
 * It provides methods to send progress updates, log messages, and other notification types
 * through a centralized notification handler.
 *
 * This interface is designed to be simple and extensible, allowing different components
 * to communicate status updates and logs in a consistent manner.
 */
interface Notifier {
    fun progress(progress: Int, total: Int, message: String?) =
        progress(progress.toDouble(), total.toDouble(), message)

    fun progress(progress: Double, total: Double, message: String?)
    fun message(msg: String) = message(
        buildJsonObject {
            put("message", msg)
        },
        LoggingLevel.info
    )

    fun message(message: JsonObject, level: LoggingLevel) = sendNotification(
        LoggingMessageNotification(
            LoggingMessageNotification.Params(
                level = level,
                logger = "Android Logcat",
                data = message,
                _meta = message
            )
        )
    )

    /**
     * Sends a notification message.
     *
     * This function adds the notification message to the global notification handler's queue,
     * which is responsible for subsequent notification distribution and processing.
     *
     * @param notification The notification object to be sent, containing relevant information and content of the notification
     * @return Returns the result of adding to the queue, the specific type depends on the return value of GlobalNotificationHandler.enqueue
     */
    fun sendNotification(notification: Notification) =
        GlobalNotificationHandler.enqueue(notification)

}



