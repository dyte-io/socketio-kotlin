import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.EnginePacket
import io.ktor.http.*
import io.ktor.util.date.*

abstract class PollingTransport: Transport {
  
  /**
    Transport name.
   */
  override var name: String = "polling"

    override var supportsBinary: Boolean = false
    var polling: Boolean = false

    /**
  * Polling interface.
    * @param {Object} opts
    * @api private
  */
  constructor(opts: TransportOptions, socket: EngineSocket?) : super(opts, socket) {
    var forceBase64 = opts.forceBase64 == true
      if (/*!hasXHR2 || */ forceBase64) {
      supportsBinary = false
    }
  }

  /**
    * Opens the socket (triggers polling). We write a PING message to determine
    * when the transport is open.
    * @api private
  */
  override fun doOpen() {
    poll()
  }

  /**
  * Pauses polling.
    * @param {Function} callback upon buffers are flushed and transport is paused
    * @api private
  */
  fun pause(onPause: () -> Unit) {
    var self = this

      readyState = "pausing"

      var pause = fun () {
      Logger.debug("Polling paused")
        self.readyState = "paused"
        onPause()
    }

      if (polling == true || writable != true) {
      var total = 0

        if (polling == true) {
          Logger.info("waiting to pause polling")
          total++
          once("pollComplete", fun (d: Any?) {
            Logger.debug("pre-pause polling complete")
            if (--total == 0) pause()
        })
      }

      if (writable != true) {
          Logger.info("write in progress - waiting to pause")
          total++
          once("drain", fun (data: Any? ) {
          Logger.debug("pre-pause writing complete")
            if (--total == 0) pause()
        })
      }
    } else {
      pause()
    }
  }

  /**
    * Starts polling cycle.
    * @api public
  */
  fun poll() {
      Logger.debug("Transport poll()")
      polling = true
      doPoll()
      emit("poll")
  }

  /**
    * Overloads onData to detect payloads.
    * @api private
  */
  override fun onData(data: String) {
    var self = this
    Logger.debug("polling onData $data")

      // decode payload
    EnginePacketParser.decodeMultiplePacket(data).forEach {
      // if its the first message we consider the transport open
      if ("opening" == self.readyState) {
        self.onOpen()
      }
      when (it) {
        is EnginePacket.Close -> self.onClose()
        else -> {
          self.onPacket(it)
        }
      }
    }

    // if an event did not trigger closing
    if ("closed" != readyState) {
      // if we got data we"re not polling
      polling = false
        emit("pollComplete")

        if ("open" == readyState) {
        poll()
      } else {
        Logger.warn("ignoring poll - transport state ${readyState}")
      }
    }
  }

  /**
    * For polling, send a close packet.
    * @api private
  */
  override fun doClose() {
    var self = this

      var _close = fun (data: Any?)  {
        Logger.debug("writing close packet")
        self.write(
        listOf(EnginePacket.Close)
      )
    }

    if ("open" == readyState) {
        Logger.info("transport open - closing")
        _close(null)
    } else {
      // in case we"re trying to close while
      // handshaking is in progress (GH-164)
      Logger.warn("transport not open - deferring close")
        once("open", _close)
    }
  }

  /**
    * Writes a packets payload.
    * @param {List} data packets
    * @param {Function} drain callback
    * @api private
  */
  override fun write(packets: List<EnginePacket>) {
    var self = this
      writable = false
      var callbackfn = fun(data: Any?) {
      self.writable = true
        self.emit("drain")
    }

      val serialized = EnginePacketParser.encodeMultiplePacket(packets)
    self.doWrite(serialized, callbackfn)
  }

  /**
    * Generates uri for connection.
    * @api private
  */
  fun uri(): String {
    var query = this.query //TODO: or else
    var schema = if(secure)  "https" else "http"
      var port = ""

      // cache busting is forced
    if (timestampRequests) {
      query = query.plus(
        Parameters.build { append(timestampParam as String, GMTDate().timestamp.toString(36)); })
    }

    if (supportsBinary == false && !query.contains("sid")) {
      query = query.plus(
        Parameters.build { append("b64", "1") }
      )
    }


    // afun port if default for schema
    if (this.port > 0 &&
        (("https" == schema && this.port != 443) ||
            ("http" == schema && this.port != 80))) {
      port = ":${this.port}"
    }

    var queryString = query.formUrlEncode()

    // prepend ? to query
    if (queryString.isNotEmpty()) {
      queryString = "?$queryString"
    }

    var ipv6 = hostname.contains(":")
      return schema +
            "://" +
            (if(ipv6) "[" + hostname + "]" else hostname) +
            port +
            path +
            queryString
  }

  abstract fun doWrite(data: String, callback: (data: Any?) -> Unit)
    abstract fun doPoll()
}
