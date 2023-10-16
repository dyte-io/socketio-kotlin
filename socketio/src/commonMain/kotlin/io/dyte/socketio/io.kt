package io.dyte.socketio

import io.ktor.http.*

object IO {
  private val cache: MutableMap<String, Manager> = mutableMapOf()

  fun socket(uri: String, opts: IOOptions): SocketClient {
    return lookup(uri, opts)
  }

  private fun lookup(uri: String, opts: IOOptions): SocketClient {

    val parsed = URLBuilder(uri)
    val id = "${parsed.protocol}://${parsed.host}:${parsed.port}"
    val path = parsed.encodedPath
    val sameNamespace = cache[id]?.nsps?.containsKey(path) ?: false
    val newConnection = opts.forceNew == true || opts.multiplex == false || sameNamespace

    val io =
      if (newConnection) {
        // Logger.fine('ignoring socket cache for $uri');
        Manager(uri, opts)
      } else {
        cache.getOrElse(id) { Manager(uri, opts) }
      }
    if (!parsed.parameters.isEmpty()) {
      opts.query = parsed.parameters.build()
    }
    return io.socket(if (parsed.pathSegments.isEmpty()) "/" else parsed.encodedPath)
  }
}

class IOOptions : ManagerOptions() {
  var multiplex: Boolean? = null
  var forceNew: Boolean? = null
}
