import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.EnginePacket
import io.dyte.socketio.src.engine.Timer
import io.ktor.http.Parameters
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.plus

class EngineSocket : EventEmitter {

  var secure: Boolean = true
  var hostname: String
  var query: Parameters
  var path: String
  var port: Int = 0
  var transports: MutableList<String>
  var upgrade: Boolean
  var transportOptions: MutableMap<String, Any>
  var policyPort: Int
  var extraHeaders: MutableMap<String, String>?
  var rememberUpgrade: Boolean

  var readyState = ""
  var writeBuffer = mutableListOf<EnginePacket>()
  var prevBufferLen = 0
  lateinit var perMessageDeflate: MutableMap<String, Any>
  var id: String? = null
  lateinit var upgrades: List<String>
  var pingInterval: Long = 4000
  var pingTimeout: Long = 8000
  var pingIntervalTimer: Timer? = null
  var pingTimeoutTimer: Timer? = null
  var transport: Transport? = null
  var supportsBinary: Boolean = false
  var upgrading: Boolean = false
  var binaryType: String? = null

  constructor(uri: String? = null, _opts: EngingeSocketOptions? = null) {

    var opts = _opts
    if (opts == null) {
      opts = EngingeSocketOptions()
    }
    if (uri != null && uri.isNotEmpty()) {
      val _uri = Url(uri)
      opts.host = _uri.host
      opts.secure = _uri.protocol == URLProtocol.HTTPS || _uri.protocol == URLProtocol.WSS
      opts.port = _uri.port
      if (_uri.parameters.isEmpty() == false) opts.query = _uri.parameters
    }

    if (opts.host != null) {
      var hostname = opts.host
      val ipv6 =
        hostname!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size > 2
      if (ipv6) {
        val start = hostname.indexOf('[')
        if (start != -1) hostname = hostname.substring(start + 1)
        val end = hostname.lastIndexOf(']')
        if (end != -1) hostname = hostname.substring(0, end)
      }
      opts.hostname = hostname
    }

    this.secure = opts.secure
    if (opts.port == -1) {
      opts.port = if (opts.secure == true) 443 else 80
    }
    this.hostname = opts.hostname ?: "localhost"
    this.port = opts.port
    this.query = opts.query ?: Parameters.build {}
    this.path = opts.path ?: "/engine.io/"
    this.transports = opts.transports
    this.upgrade = opts.upgrade ?: true
    this.transportOptions = opts.transportOptions
    this.policyPort = if (opts.policyPort != -1) opts.policyPort else 843
    this.extraHeaders = opts.extraHeaders
    this.rememberUpgrade = opts.rememberUpgrade
  }

  companion object {

    var priorWebsocketSuccess = false
    val EVENT_OPEN = "open"
    val EVENT_MESSAGE = "message"
    val EVENT_PING = "ping"
    val EVENT_UPGRADE = "upgrade"
    val EVENT_UPGRADING = "upgrading"
    val EVENT_CLOSE = "close"
    val EVENT_PACKET_CREATE = "packetCreate"
    val EVENT_UPGRADE_ERROR = "upgradeError"
    val EVENT_HANDSHAKE = "handshake"
    val EVENT_TRANSPORT = "transport"

    /**
     * Protocol version.
     *
     * @api public
     */
    val protocol = EnginePacketParser.protocol // this is an int
  }

  /*
   * Creates transport of the given type.
   * @param {String} transport name
   * @return {Transport}
   * @api private
   */
  fun createTransport(name: String, _options: TransportOptions? = null): Transport {
    Logger.debug("Creating transport $name")
    var query = this.query
    // append engine.io protocol identifier

    query =
      query.plus(
        Parameters.build {
          if (query.contains("EIO").not()) {
            append("EIO", EnginePacketParser.protocol.toString())
          }
          append("transport", name)
          // session id if we already have one
          if (id != null) append("sid", id!!)
        }
      )

    // TODO copy transport specific params
    // per-transport options
    //    var options = opts.transportOptions.getOrElse(name) { mutableMapOf<String,Any>() } as
    // MutableMap<String,Any>;
    //    _options.forEach {
    //      options[it.key] = it.value;
    //    }

    val optionsFinal = TransportOptions()
    optionsFinal.query = query
    //  optionsFinal.agent = opts.agent
    optionsFinal.hostname = this.hostname
    optionsFinal.port = this.port
    optionsFinal.secure = this.secure
    optionsFinal.path = this.path
    //        optionsFinal.forceJSONP = opts.forceJSONP
    //        optionsFinal.jsonp = opts.jsonp
    //        optionsFinal.forceBase64 = opts.forceBase64
    //        optionsFinal.enablesXDR = opts.enablesXDR
    //        optionsFinal.timestampRequests = opts.timestampRequests
    //        optionsFinal.timestampParam = opts.timestampParam
    optionsFinal.policyPort = this.policyPort
    //      optionsFinal.perMessageDeflate = opts.perMessageDeflate
    optionsFinal.extraHeaders = this.extraHeaders
    //        optionsFinal.requestTimeout = opts.requestTimeout
    val transport = Transports.newInstance(name, optionsFinal, this)
    emit(EVENT_TRANSPORT, transport)
    return transport
  }

  /**
   * Initializes transport to use and starts probe. ///
   *
   * @api private
   */
  fun open() {
    val transportName: String
    if (this.rememberUpgrade && priorWebsocketSuccess && transports.contains("websocket")) {
      transportName = "websocket"
    } else if (transports.isEmpty()) {
      // Emit error on next tick so it can be listened to
      Timer(
        1,
        fun() {
          emit("error", "No transports available")
        }
      )
      return
    } else {
      transportName = transports[0]
    }
    readyState = "opening"
    val transport: Transport
    // Retry with the next transport if the transport is disabled (jsonp: false)
    try {
      transport = createTransport(transportName)
    } catch (e: Exception) {
      Logger.error("Error opening transport", e)
      transports.removeAt(0)
      open()
      return
    }

    transport.open()
    setTransportInternal(transport)
  }

  /**
   * Sets the current transport. Disables the existing one (if any). ///
   *
   * @api private
   */
  fun setTransportInternal(transport: Transport) {
    Logger.debug("setTransportInternal ${transport.name}")

    if (this.transport != null) {
      Logger.warn("setTransportInternal clearing transport ${this.transport?.name}")
      this.transport?.clearListeners()
    }

    // set up transport
    this.transport = transport

    // set up transport listeners
    transport.on(
      "drain",
      fun(data: Any?) {
        onDrain()
      }
    )
    transport.on(
      "packet",
      fun(packet) {
        onPacket(packet as EnginePacket)
      }
    )
    transport.on(
      "error",
      fun(e: Any?) {
        onError(e)
      }
    )
    transport.on(
      "close",
      fun(data: Any?) {
        onClose("transport close", "")
      }
    )
  }

  /**
   * Probes a transport. ///
   *
   * @param {String} transport name
   * @api private
   */
  fun probe(name: String) {
    Logger.debug("probing transport ${name}")
    val probeTransportOptions = TransportOptions()
    probeTransportOptions.probe = true
    var transport: Transport? = createTransport(name, probeTransportOptions)
    var failed = false
    val cleanup = arrayListOf<() -> Unit>()

    val onTransportOpen =
      fun(data: Any?) {
        if (failed) return

        Logger.info("probe transport $name opened")
        transport?.send(listOf(EnginePacket.Ping("probe")))
        transport?.once(
          "packet",
          fun(_msg) {
            if (failed) return
            val msg = _msg as EnginePacket
            if (msg is EnginePacket.Pong && msg.payload == "probe") {
              Logger.debug("probe transport $name pong")
              upgrading = true
              emit(EVENT_UPGRADING, transport)
              if (transport == null) return
              priorWebsocketSuccess = "websocket" == transport?.name

              Logger.debug("pausing current transport ${transport?.name}")
              if (this.transport is PollingTransport) {
                (this.transport as PollingTransport).pause(
                  fun() {
                    if (failed) return
                    if ("closed" == readyState) return
                    Logger.info("changing transport and sending upgrade packet")

                    if (cleanup.size > 0) {
                      cleanup[0]()
                    }
                    setTransportInternal(transport!!)
                    transport?.send(listOf(EnginePacket.Upgrade))
                    emit(EVENT_UPGRADE, transport)
                    transport = null
                    upgrading = false
                    flush()
                  }
                )
              }
            } else {
              Logger.warn("probe transport ${name} failed ${msg}")
              emit(
                EVENT_UPGRADE_ERROR,
                mutableMapOf("error" to "probe error", "transport" to transport?.name)
              )
            }
          }
        )
      }

    val freezeTransport =
      fun() {
        if (failed) return

        // Any callback called by transport should be ignored since now
        failed = true
        if (cleanup.size > 0) {
          cleanup[0]()
        }

        transport?.close()
        transport = null
      }

    // Handle any error that happens while probing
    val onerror =
      fun(err: Any?) {
        val oldTransport = transport
        freezeTransport()

        Logger.warn("probe transport ${name} failed because of error: $err")

        emit(
          EVENT_UPGRADE_ERROR,
          mutableMapOf("error" to "probe error: $err", "transport" to oldTransport?.name)
        )
      }

    val onTransportClose =
      fun(data: Any?) {
        onerror("transport closed")
      }

    // When the socket is closed while we"re probing
    val onclose =
      fun(data: Any?) {
        onerror("socket closed")
      }

    // When the socket is upgraded while we"re probing
    val onupgrade =
      fun(_to: Any?) {
        val to = _to as Transport?
        if (transport != null && to?.name != transport?.name) {
          Logger.info("${to?.name} works - aborting ${transport?.name}")
          freezeTransport()
        }
      }

    // Remove all listeners on the transport and on self
    cleanup.add(
      0,
      fun() {
        transport?.off("open", onTransportOpen)
        transport?.off("error", onerror)
        transport?.off("close", onTransportClose)
        off("close", onclose)
        off("upgrading", onupgrade)
      }
    )

    transport?.once("open", onTransportOpen)
    transport?.once("error", onerror)
    transport?.once("close", onTransportClose)

    once("close", onclose)
    once("upgrading", onupgrade)

    transport?.open()
  }

  /**
   * Called when connection is deemed open. ///
   *
   * @api public
   */
  fun onOpen() {
    Logger.debug("socket open")
    readyState = "open"
    priorWebsocketSuccess = "websocket" == transport?.name
    emit(EVENT_OPEN)
    flush()

    // we check for `readyState` in case an `open`
    // listener already closed the socket
    // TODO: upgrade to WS
    if ("open" == readyState && upgrade == true && transport is PollingTransport) {
      Logger.debug("starting upgrade probes")
      for (i in 0..(upgrades.size - 1)) {
        probe(upgrades[i])
      }
    }
  }

  /**
   * Handles a packet. ///
   *
   * @api private
   */
  fun onPacket(packet: EnginePacket) {
    if ("opening" == readyState || "open" == readyState || "closing" == readyState) {

      emit("packet", packet)

      // Socket is live - any packet counts
      emit("heartbeat")

      // var type = packet.type;
      //            var data = packet.data;

      when (packet) {
        is EnginePacket.Open -> {
          onHandshake(packet)
        }
        is EnginePacket.Ping -> {
          resetPingTimeout()
          sendPacket(EnginePacket.Pong())
          emit(EVENT_PING)
        }
        is EnginePacket.Error -> {
          onError("server error")
        }
        is EnginePacket.Message -> {
          Logger.debug("onMessage enginesocket")
          emit("data", packet.payload)
          emit(EVENT_MESSAGE, packet.payload)
        }
        is EnginePacket.BinaryMessage -> {
          Logger.debug("onMessage enginesocket")
          emit("data", packet.payload)
          emit(EVENT_MESSAGE, packet.payload)
        }
        else -> {}
      }
    } else {
      Logger.warn("packet received with socket readyState $readyState")
    }
  }

  /** Sets and resets ping timeout timer based on server pings. */
  fun resetPingTimeout() {
    pingTimeoutTimer?.cancel()
    pingTimeoutTimer =
      Timer(
        pingInterval + pingTimeout,
        fun() {
          onClose("ping timeout", "")
        }
      )
    pingTimeoutTimer?.schedule()
  }

  /**
   * Called upon handshake completion. ///
   *
   * @param {Object} handshake obj
   */
  fun onHandshake(data: EnginePacket.Open) {
    emit(EVENT_HANDSHAKE, data)
    Logger.debug("onHandshake")
    id = data.sid
    transport!!.query = transport?.query!!.plus(Parameters.build { append("sid", data.sid) })

    upgrades = filterUpgrades(data.upgrades)
    pingInterval = data.pingInterval
    pingTimeout = data.pingTimeout
    onOpen()
    // In case open handler closes socket
    if ("closed" == readyState) return
    resetPingTimeout()
  }

  /**
   * Called on `drain` event ///
   *
   * @api private
   */
  fun onDrain() {
    writeBuffer = writeBuffer.subList(prevBufferLen, writeBuffer.size)

    // setting prevBufferLen = 0 is very important
    // for example, when upgrading, upgrade packet is sent over,
    // and a nonzero prevBufferLen could cause problems on `drain`
    prevBufferLen = 0

    if (writeBuffer.isEmpty()) {
      emit("drain")
    } else {
      flush()
    }
  }

  /**
   * Flush write buffers. ///
   *
   * @api private
   */
  fun flush() {
    Logger.info("Engine flush")
    if (
      "closed" != readyState &&
        transport?.writable == true &&
        upgrading != true &&
        writeBuffer.isNotEmpty()
    ) {
      Logger.debug("flushing ${writeBuffer.size} packets in socket")
      // keep track of current length of writeBuffer
      // splice writeBuffer and callbackBuffer on `drain`
      prevBufferLen = writeBuffer.size
      transport?.send(writeBuffer.toList())
      emit("flush")
    }
  }

  /**
   * Sends a message. ///
   *
   * @param {String} message.
   * @param {Function} callback function.
   * @param {Object} options.
   * @return {Socket} for chaining.
   * @api public
   */
  fun write(msg: String, callback: ((data: Any?) -> Unit)? = null): EngineSocket {
    return send(msg, callback)
  }

  fun send(msg: String, callback: ((data: Any?) -> Unit)? = null): EngineSocket {
    sendPacket(EnginePacket.Message(msg), callback = callback)
    return this
  }

  fun send(msg: ByteArray, callback: ((data: Any?) -> Unit)? = null) {
    sendPacket(EnginePacket.BinaryMessage(msg), callback = callback)
  }

  /**
   * Sends a packet. ///
   *
   * @param {String} packet type.
   * @param {String} data.
   * @param {Object} options.
   * @param {Function} callback function.
   * @api private
   */
  fun sendPacket(packet: EnginePacket, callback: ((data: Any?) -> Unit)? = null) {
    if ("closing" == readyState || "closed" == readyState) {
      return
    }
    emit(EVENT_PACKET_CREATE, packet)
    writeBuffer.add(packet)
    if (callback != null) once("flush", callback)
    flush()
  }

  /**
   * Closes the connection.
   *
   * @api private
   */
  fun close(): EngineSocket {
    val close =
      fun() {
        onClose("forced close", "")
        Logger.info("socket closing - telling transport to close")
        transport?.close()
      }

    var cleanupAndClose = arrayListOf<(Any?) -> Unit>()

    cleanupAndClose.add(
      0,
      fun(data: Any?) {
        if (cleanupAndClose.size > 0) {
          off("upgrade", cleanupAndClose[0])
          off("upgradeError", cleanupAndClose[0])
        }
        close()
      }
    )

    // a workaround for dart to access the local variable;

    val waitForUpgrade =
      fun() {
        if (cleanupAndClose.size > 0) {
          // wait for upgrade to finish since we can"t send packets while pausing a transport
          once("upgrade", cleanupAndClose[0])
          once("upgradeError", cleanupAndClose[0])
        }
      }

    if ("opening" == readyState || "open" == readyState) {
      readyState = "closing"

      if (writeBuffer.isNotEmpty()) {
        once(
          "drain",
          fun(data: Any?) {
            if (upgrading == true) {
              waitForUpgrade()
            } else {
              close()
            }
          }
        )
      } else if (upgrading == true) {
        waitForUpgrade()
      } else {
        close()
      }
    }

    return this
  }

  /** Called upon transport error */
  fun onError(_err: Any?) {
    val err: String
    if (_err is String) {
      err = _err
    } else {
      val m = _err as Map<String, String?>
      err = "${m.get("map")} ${m.get("desc")}"
    }
    Logger.error("socket error $err")
    priorWebsocketSuccess = false
    emit("error", err)
    onClose("transport error", err)
  }

  /** Called upon transport close */
  fun onClose(reason: String, desc: String) {
    if ("opening" == readyState || "open" == readyState || "closing" == readyState) {
      Logger.warn("socket close with reason: $reason")

      // clear timers
      pingIntervalTimer?.cancel()
      pingTimeoutTimer?.cancel()

      // stop event from firing again for transport
      transport?.off(EVENT_CLOSE, null)

      // ensure transport won"t stay open
      transport?.close()

      // ignore further transport communication
      transport?.clearListeners()

      // set ready state
      readyState = "closed"

      // clear session id
      id = null

      // emit close event
      emit(EVENT_CLOSE, mapOf("reason" to reason, "desc" to desc))

      // clean buffers after, so users can still
      // grab the buffers on `close` event
      writeBuffer.clear()
      prevBufferLen = 0
    }
  }

  /**
   * Filters upgrades, returning only those matching client transports. ///
   *
   * @param {Array} server upgrades
   * @api private
   */
  fun filterUpgrades(upgrades: List<String>): List<String> {
    return upgrades.filter { transports.contains(it) }
  }
}

open class EngingeSocketOptions : TransportOptions() {
  var host: String? = null
  var transports: MutableList<String> = mutableListOf("polling", "websocket")
  var transportOptions: MutableMap<String, Any> = mutableMapOf<String, Any>()
  var rememberUpgrade: Boolean = false
  var binaryType: Nothing? = null
  var onlyBinaryUpgrades: Boolean = false
}
