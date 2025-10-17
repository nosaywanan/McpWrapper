package open.ai.mcp.core

import io.modelcontextprotocol.kotlin.sdk.Notification
/**
 * GlobalNotificationHandler is a singleton object responsible for managing and distributing
 * notifications throughout the application. It implements the Observer pattern to allow
 * multiple components to subscribe to and receive notifications.
 *
 * This handler ensures that notifications are properly propagated to all registered observers
 * in a thread-safe manner.
 */
object GlobalNotificationHandler {

    interface Observer{
        fun onNotification(notification: Notification)
    }
    /**
     * Notifies all registered observers about a new notification.
     *
     * This method iterates through the list of registered observers and calls
     * their `onNotification` method, passing the received notification.
     *
     * Execution is performed synchronously on the calling thread. If long-running
     * operations are required, observers should handle them asynchronously.
     *
     * @param notification The notification to be propagated to all observers.
     */
    fun enqueue(notification: Notification) {
        observers.forEach { it.onNotification(notification) }
    }

    /**
     * A thread-safe list of observers that are registered to receive notifications.
     * This list is modified only through the [register] and [unregister] methods,
     * ensuring that all operations on the list are controlled and safe.
     */
    private val observers = ArrayList<Observer>()

    fun register(observer: Observer){
        if (observers.contains(observer)) return
        observers.add(observer)
    }
    fun unregister(observer: Observer){
        observers.remove(observer)
    }
}