package io.dyte.socketio.engine.transport

import io.dyte.socketio.engine.EngineSocket

class Transports {
  companion object {
    fun upgradesTo(from: String): List<String> {
      if (from == "polling") {
        return listOf("websocket")
      }
      return listOf()
    }

    fun newInstance(name: String, options: TransportOptions, socket: EngineSocket): Transport {
      if ("websocket" == name) {
        return WebSocketTransport(options, socket)
      } else if ("polling" == name) {
        if (options.forceJSONP != true) {
          return XHRTransport(options, socket)
        } else {
          //          if (options["jsonp"] != false) return JSONPTransport(options);
          throw IllegalStateException("JSONP disabled")
        }
      } else {
        throw Exception("Unknown transport $name")
      }
    }
  }
}
