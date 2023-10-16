package io.dyte.socketio

enum class LogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG
}

interface ExternalLogger {
  fun debug(message: String)

  fun info(message: String)

  fun warn(message: String, cause: Throwable? = null)

  fun error(message: String, cause: Throwable? = null)
}

object StdoutLogger : ExternalLogger {
  override fun debug(message: String) {
    println(message)
  }

  override fun info(message: String) {
    println(message)
  }

  override fun warn(message: String, cause: Throwable?) {
    println(message)
    cause?.printStackTrace()
  }

  override fun error(message: String, cause: Throwable?) {
    println(message)
    cause?.printStackTrace()
  }
}

object Logger {
  val instance = "SocketIO"
  private var level = LogLevel.WARN
  private var externalLogger: ExternalLogger = StdoutLogger

  fun setExternalLogger(l: ExternalLogger) {
    this.externalLogger = l
  }

  fun error(message: String, cause: Throwable? = null) {
    val m = "$instance::$message : $cause"
    externalLogger.error(m, cause)
  }

  fun warn(message: String, cause: Throwable? = null) {
    if (level.ordinal < LogLevel.WARN.ordinal) return
    val m = "$instance::$message::$cause"
    externalLogger.warn(m, cause)
  }

  fun info(message: String) {
    if (level.ordinal < LogLevel.INFO.ordinal) return
    val m = "$instance::$message"
    externalLogger.info(m)
  }

  fun debug(message: String) {
    if (level.ordinal < LogLevel.DEBUG.ordinal) return
    val m = "$instance::$message"
    externalLogger.debug(m)
  }

  fun setLevel(l: LogLevel) {
    level = l
  }
}
