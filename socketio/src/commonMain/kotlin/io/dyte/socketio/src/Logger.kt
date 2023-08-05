package io.dyte.socketio.src

import io.ktor.util.logging.KtorSimpleLogger

enum class LogLevel {
    ERROR, WARN, INFO, DEBUG
}
object Logger {
    val instance = KtorSimpleLogger("SocketIO")
    val level = LogLevel.WARN;
    fun error(message: String) {
        instance.error(message)
    }

    fun error(message: String, cause: Throwable) {
        instance.error(message, cause)
    }

    fun warn(message: String) {
        if(level.ordinal < LogLevel.WARN.ordinal) return
        instance.error(message)
    }

    fun warn(message: String, cause: Throwable) {
        if(level.ordinal < LogLevel.WARN.ordinal) return
        instance.error(message, cause)
    }

    fun info(message: String) {
        if(level.ordinal < LogLevel.INFO.ordinal) return
        instance.error(message)
    }


    fun debug(message: String) {
        if(level.ordinal < LogLevel.DEBUG.ordinal) return
        instance.error(message)
    }

}