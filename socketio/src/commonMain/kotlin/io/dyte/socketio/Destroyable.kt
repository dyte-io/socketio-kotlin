package io.dyte.socketio

class Destroyable(private val callback: () -> Unit) {
  fun destroy() {
    callback()
  }

  companion object {
    fun new(emitter: EventEmitter, eventName: String, callback: (data: Any?) -> Unit): Destroyable {
      emitter.on(eventName, callback)
      return Destroyable {
        Logger.debug("DESTROYING event listener for $eventName")
        emitter.off(eventName, callback)
      }
    }
  }
}
