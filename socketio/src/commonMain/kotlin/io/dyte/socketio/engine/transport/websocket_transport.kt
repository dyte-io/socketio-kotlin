package io.dyte.socketio.engine.transport

import io.dyte.socketio.Logger
import io.dyte.socketio.engine.EnginePacket
import io.dyte.socketio.engine.EnginePacketParser
import io.dyte.socketio.engine.EngineSocket
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.headers
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.plus
import io.ktor.util.date.GMTDate
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking

class WebSocketTransport : Transport {

  override var name = "websocket"
  //  var protocols: MutableList<String>?;

  val serialScope = CoroutineScope(newSingleThreadContext("WSScope${hashCode()}"))

  override var supportsBinary: Boolean

  //  lateinit var perMessageDeflate: MutableMap<String, Any>;
  var ws: WebSocketSession? = null
  var scope: CoroutineScope? = null
  val client = HttpClient { install(WebSockets) }

  constructor(opts: TransportOptions, socket: EngineSocket? = null) : super(opts, socket) {
    var forceBase64 = opts.forceBase64 == true
    supportsBinary = !forceBase64
  }

  override fun doOpen() {
    val ur = this.uri()

    Logger.info("Opening websocket ${this.uri()}")
    // TODO: DYTE
    scope = CoroutineScope(Dispatchers.Default)
    scope!!.launch {
      try {
        ws =
          client.webSocketSession(ur) {
            headers {
              append("Accept", "*/*")
              extraHeaders?.forEach { append(it.key, it.value) }
            }
          }
        onOpen()
        listen()
      } catch (err: Exception) {
        Logger.error("Websocket open error", err)
        emit("error", err)
      }
    }
  }

  fun listen() {
    scope!!.launch {
      while (true) {
        try {
          val frame = ws!!.incoming.receive()
          Logger.debug("Websocket frame recieved ${frame.frameType}")
          when (frame) {
            is Frame.Text -> {
              val ft = frame.readText()
              onData(ft)
            }
            is Frame.Binary -> {
              val ft = frame.readBytes()
              onData(ft)
            }
            is Frame.Close -> {
              onClose()
              break
            }
            else -> {
              Logger.warn("Received unknown frame type ${frame.frameType}")
            }
          }
        } catch (e: Exception) {
          Logger.error("Error while reading websocket frame", e)
          onClose()
          break
        }
      }
    }
  }

  /// Writes data to socket.
  ///
  /// @param {Array} array of packets.
  /// @api private
  override fun write(packets: List<EnginePacket>) {
    writable = false

    var done =
      fun() {
        writable = true
        emit("drain")
      }

    var total = packets.size
    packets.forEach {
      val packet = it
      try {
        serialScope.launch {
          if (it is EnginePacket.BinaryMessage) {
            ws?.send(it.payload)
          } else {
            val data = EnginePacketParser.encodePacket(packet)
            ws?.send(data)
          }
        }
      } catch (e: Error) {
        Logger.error("websocket closed while writing", e)
      }

      if (--total == 0) done()
    }
  }

  /// Closes socket.
  ///
  /// @api private

  override fun doClose() {
    runBlocking { ws?.close() }
  }

  /// Generates uri for connection.
  ///
  /// @api private
  fun uri(): String {
    var query = this.query // TODO: or else
    var schema = if (secure) "wss" else "ws"
    var port = ""

    // afun port if default for schema
    if (
      this.port > 0 &&
        (("wss" == schema && this.port != 443) || ("ws" == schema && this.port != 80))
    ) {
      port = ":${this.port}"
    }

    // cache busting is forced
    if (timestampRequests) {
      query =
        query.plus(
          Parameters.build { append(timestampParam as String, GMTDate().timestamp.toString(36)) }
        )
    }

    if (supportsBinary == false) {
      query = query.plus(Parameters.build { append("b64", "1") })
    }

    var queryString = query.formUrlEncode()

    // prepend ? to query
    if (queryString.isNotEmpty()) {
      queryString = "?$queryString"
    }

    var ipv6 = hostname.contains(":")
    return schema +
      "://" +
      (if (ipv6) "[" + hostname + "]" else hostname) +
      port +
      path +
      queryString
  }
}
