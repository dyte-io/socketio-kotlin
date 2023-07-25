package io.dyte.socketio.src.engine



class EnginePacket<T> constructor(var type: String, data: T? = null) {
    var data: T?

    init {
        this.data = data
    }

    companion object {
        const val OPEN = "open"
        const val CLOSE = "close"
        const val PING = "ping"
        const val PONG = "pong"
        const val UPGRADE = "upgrade"
        const val MESSAGE = "message"
        const val NOOP = "noop"
        const val ERROR = "error"
    }
}