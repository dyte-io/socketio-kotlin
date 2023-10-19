package io.dyte.socketio

/** Generic event emitting and handling. */
open class EventEmitter {
  /** Mapping of events to a list of event handlers */
  private val events = mutableMapOf<String, MutableList<(data: Any?) -> Unit>>()

  /** Mapping of events to a list of one-time event handlers */
  private val eventsOnce = mutableMapOf<String, MutableList<(data: Any?) -> Unit>>()

  /** List of handlers that listen every event */
  private val eventsAny = mutableListOf<(event: String, data: Any?) -> Unit>()

  /**
   * This function triggers all the handlers currently listening to [event] and passes them [data].
   */
  open fun emit(event: String, data: Any? = null) {
    val list = (this.events.getOrElse(event) { listOf() }).toList()
    Logger.debug("${list.size} event listeners registered for $event")

    // todo: try to optimize this. Maybe remember the off() handlers and remove later?
    // handler might be off() inside handler; make a copy first
    list.forEach { it(data) }

    this.eventsOnce.remove(event)?.forEach { it(data) }

    this.eventsAny.forEach { it(event, data) }
  }

  /** This function binds the [handler] as a listener to the [event] */
  fun on(event: String, handler: (data: Any?) -> Unit) {
    this.events.getOrPut(
      event,
      fun(): MutableList<(data: Any?) -> Unit> {
        return mutableListOf<(data: Any?) -> Unit>()
      }
    )
    this.events[event]?.add(handler)
  }

  /**
   * This function binds the [handler] as a listener to the first occurrence of the [event]. When
   * [handler] is called once, it is removed.
   */
  fun once(event: String, handler: (data: Any?) -> Unit) {
    this.eventsOnce.getOrPut(
      event,
      fun(): MutableList<(data: Any?) -> Unit> {
        return mutableListOf<(data: Any?) -> Unit>()
      }
    )
    this.eventsOnce[event]?.add(handler)
  }

  /** This function binds the [handler] as a listener to any event */
  fun onAny(handler: (event: String, data: Any?) -> Unit) {
    this.eventsAny.add(handler)
  }

  /** This function attempts to unbind the [handler] from the [event] */
  fun off(event: String, handler: ((data: Any?) -> Unit)?) {
    if (handler != null) {
      this.events[event]?.remove(handler)
      this.eventsOnce[event]?.remove(handler)
      if (this.events[event]?.isEmpty() == true) {
        this.events.remove(event)
      }
      if (this.eventsOnce[event]?.isEmpty() == true) {
        this.eventsOnce.remove(event)
      }
    } else {
      this.events.remove(event)
      this.eventsOnce.remove(event)
    }
  }

  /**
   * This function attempts to unbind the [handler]. If the given [handler] is null, this function
   * unbinds all any event handlers.
   */
  fun offAny(handler: ((event: String, data: Any?) -> Unit)?) {
    if (handler != null) {
      this.eventsAny.remove(handler)
    } else {
      this.eventsAny.clear()
    }
  }

  /** This function unbinds all the handlers for all the events. */
  fun clearListeners() {
    this.events.clear()
    this.eventsOnce.clear()
    this.eventsAny.clear()
  }

  /** Returns whether the event has registered. */
  fun hasListeners(event: String): Boolean {
    return this.events[event]?.isNotEmpty() == true || this.eventsOnce[event]?.isNotEmpty() == true
  }
}
