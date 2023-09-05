package io.dyte.socketio.src

enum class LogLevel {
  ERROR,
  WARN,
  INFO,
  DEBUG
}

interface ExternalLogger {
  fun debug(message: String)

  fun info(message: String)

  fun warn(message: String)

  fun error(message: String)
}

object Logger {
  val instance = "SocketIO"
  private var level = LogLevel.WARN
  private lateinit var l: ExternalLogger

  fun setExternalLogger(l: ExternalLogger) {
    this.l = l
  }

  fun error(message: String, cause: Throwable? = null) {
    val m ="$instance::$message : $cause"
    if(this::l.isInitialized){
      l.error(m)
    } else {
      println(m)
      cause?.printStackTrace()
    }
  }


  fun warn(message: String, cause: Throwable? = null) {
    if (level.ordinal < LogLevel.WARN.ordinal) return
    val m = "$instance::$message::$cause";
    if(this::l.isInitialized){
      l.warn(m)
    } else {
      println(m)
      cause?.printStackTrace()
    }
  }

  fun info(message: String) {
    if (level.ordinal < LogLevel.INFO.ordinal) return
    val m = "$instance::$message"
    if(this::l.isInitialized){
      l.info(m)
    } else {
      println(m)
    }
  }

  fun debug(message: String) {
    if (level.ordinal < LogLevel.DEBUG.ordinal) return
    val m = "$instance::$message"
    if(this::l.isInitialized){
      l.debug(m)
    } else {
      println(m)
    }
  }

  fun setLevel(l: LogLevel) {
    level = l
  }
}
