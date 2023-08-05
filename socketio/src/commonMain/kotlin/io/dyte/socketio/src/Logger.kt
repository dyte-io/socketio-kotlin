package io.dyte.socketio.src

enum class LogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG
}

object Logger {
  val instance = "SocketIO"
  private var level = LogLevel.WARN

  fun error(message: String) {
    println("$instance::error::$message")
  }

  fun error(message: String, cause: Throwable) {
    println("$instance::error::$message : $cause")
    cause.printStackTrace()
  }

  fun warn(message: String) {
    if (level.ordinal < LogLevel.WARN.ordinal) return
    println("$instance::warn::$message")
  }

  fun warn(message: String, cause: Throwable) {
    if (level.ordinal < LogLevel.WARN.ordinal) return
    println("$instance::warn::$message : $cause")
  }

  fun info(message: String) {
    if (level.ordinal < LogLevel.INFO.ordinal) return
    println("$instance::info::$message")
  }

  fun debug(message: String) {
    if (level.ordinal < LogLevel.DEBUG.ordinal) return
    println("$instance::debug::$message")
  }

  fun setLevel(l: LogLevel) {
    level = l
  }
}
