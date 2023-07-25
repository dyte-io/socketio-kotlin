package io.dyte.socketio.src

class Logger {
    companion object {
        fun fine(l: String)
        {
            println("SocketIO:: ${l}")
        }
    }
}