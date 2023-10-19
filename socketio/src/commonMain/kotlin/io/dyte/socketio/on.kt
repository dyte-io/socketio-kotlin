package io.dyte.socketio

object Util {
  fun on(obj: EventEmitter, ev: String, fn: (data: Any?) -> Unit): Destroyable {
    obj.on(ev, fn)
    return Destroyable {
      Logger.debug("DESTROYING event listener for $ev")
      obj.off(ev, fn)
    }
  }
}

class Destroyable(private val callback: () -> Unit) {

  fun destroy() {
    callback()
  }
}
