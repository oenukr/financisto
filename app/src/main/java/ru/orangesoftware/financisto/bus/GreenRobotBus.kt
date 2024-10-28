package ru.orangesoftware.financisto.bus

import org.greenrobot.eventbus.EventBus

class GreenRobotBus {

    val bus: EventBus = EventBus()

    fun post(event: Any) = bus.post(event)

    fun postSticky(event: Any) = bus.postSticky(event)

    fun <T> removeSticky(eventClass: Class<T>): T = bus.removeStickyEvent(eventClass)

    fun register(subscriber: Any) {
        if (!bus.isRegistered(subscriber)) {
            bus.register(subscriber)
        }
    }

    fun unregister(subscriber: Any) = bus.unregister(subscriber)
}
