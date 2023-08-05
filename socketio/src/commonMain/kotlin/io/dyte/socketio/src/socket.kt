import io.dyte.socketio.src.ClientPacket
import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.utils
import io.ktor.http.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

typealias ACKFn = Function1<Any?, Unit>?

class SocketClient(io: Manager, nsp: String, opts: ManagerOptions) : EventEmitter() {
  var CONNECT = 0
  var DISCONNECT = 1
  var EVENT = 2
  var ACK = 3
  var CONNECT_ERROR = 4
  var BINARY_EVENT = 5
  var BINARY_ACK = 6

  var EVENTS =
    listOf(
      "connect",
      "connect_error",
      "connect_timeout",
      "connecting",
      "disconnect",
      "error",
      "reconnect",
      "reconnect_attempt",
      "reconnect_failed",
      "reconnect_error",
      "reconnecting",
      "ping",
      "pong"
    )

  companion object {
    val EVENT_CONNECT = "connect"
    val EVENT_DISCONNECT = "disconnect"
  }

  val nsp: String = nsp
  val opts: ManagerOptions = opts

  val io: Manager = io
  val json: SocketClient = this
  var ids = 0
  val acks: MutableMap<String, ((data: Any?) -> Unit)> =
    mutableMapOf<String, ((data: Any?) -> Unit)>()
  var connected = false
  var disconnected = true
  var sendBuffer = mutableListOf<ClientPacket<*>>()
  var receiveBuffer = mutableListOf<MutableList<Any>>()
  val query: Parameters? = opts.query
  val auth: Any? = opts.auth
  var subs: MutableList<Destroyable>? = mutableListOf<Destroyable>()
  var flags = mutableMapOf<String, Boolean>()
  var id: String? = ""
  // TODO: DYTE
  //    if (io.autoConnect) open();

  /** Whether the Socket will try to reconnect when its Manager connects or reconnects */
  fun getActive(): Boolean {
    return subs != null
  }

  /**
   * "Opens" the socket.
   *
   * @api public
   */
  fun open() {
    connect()
  }

  fun connect(): SocketClient {
    if (connected) return this
    subEvents()
    if (!io.reconnecting) {
      io.open(opt = this.opts) // ensure open
    }
    if (io.readyState == "open") onopen(null)
    return this
  }

  /**
   * Sends a `message` event.
   *
   * @return {Socket} self
   * @api public
   */
  fun send(args: List<Any>): SocketClient {
    emit("message", args)
    return this
  }

  /**
   * Override `emit`. If the event is in `events`, it"s emitted normally.
   *
   * @param {String} event name
   * @return {Socket} self
   * @api public
   */
  fun emit(event: String, vararg data: Any?) {
    if (data.size > 0 && data.last() is Function1<*, *>) {
      _emitWithAck(
        event,
        data.toList().subList(0, data.size - 1),
        data.last() as Function1<Any?, Unit>
      )
    } else {
      _emitWithAck(event, data.toList())
    }
  }

  override fun emit(event: String, data: Any?) {
    _emitWithAck(event, data)
  }

  fun onConnect(callback: () -> Unit) {
    on(
      EVENT_CONNECT,
      fun(_) {
        callback()
      }
    )
  }

  fun onDisconnect(callback: () -> Unit) {
    on(
      EVENT_DISCONNECT,
      fun(_) {
        callback()
      }
    )
  }

  fun onEvent(event: String, callback: (data: ArrayList<JsonElement>) -> Unit) {
    onEvent(
      event,
      fun(data: ArrayList<JsonElement>, ack: ACKFn) {
        callback(data)
      }
    )
  }

  fun onEvent(event: String, callback: (data: ArrayList<JsonElement>, ack: ACKFn) -> Unit) {
    on(
      event,
      fun(data: Any?) {
        if (data is Function1<*, *>) {
          callback(ArrayList(), data as ACKFn)
        } else if (data is ArrayList<*>) {
          var ackElem: Function1<Any?, Unit>? = null
          try {
            val lastElem = data.last()
            if (lastElem is Function1<*, *>) {
              ackElem = lastElem as ACKFn
            }
          } catch (e: Exception) {}
          val _data = data as ArrayList<JsonElement>
          if (ackElem != null) {
            // all data except the ack callback
            callback(ArrayList(_data.subList(0, data.size - 1)), ackElem)
          } else {
            callback(_data, null)
            callback(_data, null)
          }
        } else {
          Logger.warn("Invalid data received for event $event")
        }
      }
    )
  }

  fun emitWithAck(
    event: String,
    vararg data: Any?,
    ack: ((data: Any?) -> Unit)? = null,
  ) {
    _emitWithAck(event, data.toList(), ack)
  }

  /**
   * Emits to this client.
   *
   * @return {Socket} self
   * @api public
   */
  fun _emitWithAck(
    event: String,
    data: Any? = null,
    ack: ((data: Any?) -> Unit)? = null,
  ) {
    if (EVENTS.contains(event)) {
      super.emit(event, data)
    } else {
      val sendData = mutableListOf<JsonElement>(JsonPrimitive(event))

      if (data is List<*>) {
        data.forEach { utils.handlePrimitive(sendData, it) }
      } else {
        utils.handlePrimitive(sendData, data)
      }

      val packet = ClientPacket(EVENT, sendData)
      //                "options" to mutableMapOf<String, Boolean>("compress" to
      // flags.getOrElse("compress") { "false "} as Boolean)

      // event ack callback
      if (ack != null) {
        Logger.info("emitting packet with ack id $ids")
        acks["$ids"] = ack
        packet.id = ids++
      }
      val isTransportWritable = io.engine.transport != null && io.engine.transport?.writable == true

      val discardPacket = flags.get("volatile") != null && (!isTransportWritable || !connected)
      if (discardPacket) {
        Logger.warn("discard packet as the transport is not currently writable")
      } else if (connected) {
        this.packet(packet as ClientPacket<*>)
      } else {
        sendBuffer.add(packet)
      }
      flags.clear() // TODO: Recheck
    }
  }

  /**
   * Sends a packet.
   *
   * @param {Object} packet
   */
  private fun packet(packet: ClientPacket<*>) {
    packet.nsp = nsp
    io.packet(packet)
  }

  /**
   * Called upon engine `open`.
   *
   */
  private fun onopen(data: Any?) {
    Logger.info("transport is open - connecting")


    if (auth != null) {
      packet(ClientPacket(CONNECT, auth))
    } else {
      packet(ClientPacket<Any>(CONNECT))
    }
  }

  /** Called upon engine or manager `error` */
  private fun onerror(err: Any?) {
    if (!connected) {
      emit("connect_error", err)
    }
  }

  /**
   * Called upon engine `close`.
   *
   * @param {String} reason
   */
  private fun onclose(data: Any?) {
    val reason = data as String
    Logger.warn("Socket close ($reason)")
    emit("disconnecting", reason)
    connected = false
    disconnected = true
    id = null
    emit(EVENT_DISCONNECT, reason)
  }

  /**
   * Called with socket packet.
   *
   * @param {Object} packet
   */
  private fun onpacket(_packet: Any?) {
    val packet = _packet as ClientPacket<*>
    if (packet.nsp != nsp) return
    Logger.debug("onPacket socket ${packet.type}")
    when (packet.type) {
      CONNECT -> {
        if (packet.data != null && (packet.data as JsonObject)["sid"] != null) {
          val id = (packet.data as JsonObject)["sid"].toString()
          onconnect(id)
        } else {
          emit(
            "connect_error",
            "It seems you are trying to reach a Socket.IO server in v2.x with a v3.x+ client, but they are not compatible (more information here: https://socket.io/docs/v3/migrating-from-2-x-to-3-0/)"
          )
        }
      }
      EVENT -> onevent(packet)
      BINARY_EVENT -> onevent(packet)
      ACK -> onack(packet)
      BINARY_ACK -> onack(packet)
      DISCONNECT -> ondisconnect()
      CONNECT_ERROR -> emit("error", packet.data)
    }
  }

  /**
   * Subscribe to open, close and packet events
   *
   */
  private fun subEvents() {
    if (subs?.isNotEmpty() == true) return
    val io = this.io
    subs =
      mutableListOf(
        Util.on(io, "open", ::onopen),
        Util.on(io, "packet", ::onpacket),
        Util.on(io, "error", ::onerror),
        Util.on(io, "close", ::onclose)
      )
  }

  /**
   * Called upon a server event.
   *
   * @param {Object} packet
   */
  private fun onevent(packet: ClientPacket<*>) {
    val args = ((packet.data ?: buildJsonArray {}) as JsonArray).toMutableList<Any>()

    if (packet.id != null) {
      args.add(ack(packet.id))
    }
    Logger.debug("onEvent size ${args.size} $connected")
    if (connected == true) {
      try {
        super.emit(
          args.first().toString().removePrefix("\"").removeSuffix("\""),
          ArrayList(args.subList(1, args.size))
        )
      } catch (e: Exception) {
        Logger.error("args socket error emit", e)
      }
    } else {
      receiveBuffer.add(args)
    }
  }

  /**
   * Produces an ack callback to emit with an event.
   *
   * @api private
   */
  private fun ack(id: Int): (Any) -> Unit {
    var sent = false
    return fun(data: Any?) {
      // prevent double callbacks
      if (sent) return
      sent = true
      Logger.info("sending ack $data")

      val sendData = mutableListOf<JsonElement>()
      if (data is List<*>) {
        data.forEach { utils.handlePrimitive(sendData, it) }
      } else {
        utils.handlePrimitive(sendData, data)
      }
      val p = ClientPacket(ACK, sendData)
      p.id = id
      packet(p)
    }
  }

  /**
   * Called upon a server acknowledgement.
   *
   * @param {Object} packet
   */
  private fun onack(packet: ClientPacket<*>) {
    val ack_ = acks.remove("${packet.id}")
    if (ack_ is Function1<*, *>) {
      Logger.info("calling ack ${packet.id} with ${packet.data}")

      val args = packet.data as JsonArray
      ack_(ArrayList(args.toList()))
    } else {
      Logger.warn("bad ack ${packet.id}")
    }
  }

  /**
   * Called upon server connect.
   *
   */
  private fun onconnect(id: String) {
    this.id = id
    connected = true
    disconnected = false
    emit("connect")
    emitBuffered()
  }

  /**
   * Emit buffered events (received and emitted).
   *
   */
  private fun emitBuffered() {
    println("Emitting buffered")
    receiveBuffer.forEach { args ->
      super.emit(
        args.first().toString().removePrefix("\"").removeSuffix("\""),
        ArrayList(args.subList(1, args.size))
      )
    }
    receiveBuffer.clear()

    sendBuffer.forEach {
        packet(it)
    }
    sendBuffer.clear()
  }

  /**
   * Called upon server disconnect.
   *
   * @api private
   */
  private fun ondisconnect() {
    Logger.warn("server disconnect ($nsp)")
    destroy()
    onclose("io server disconnect")
  }

  /**
   * Called upon forced client/server side disconnections, this method ensures the manager stops
   * tracking us and that reconnections don"t get triggered for this.
   *
   */
  private fun destroy() {
    val _subs = subs
    if (_subs != null && _subs.isNotEmpty()) {
      // clean subscriptions to avoid reconnections

      for (i in 0..(_subs.size - 1)) {
        _subs[i].destroy()
      }
      subs = null
    }

    io.destroy(this)
  }

  /**
   * Disconnects the socket manually.
   *
   * @return {Socket} self
   */
  fun close(): SocketClient {
    return disconnect()
  }

  fun disconnect(): SocketClient {
    if (connected == true) {
      Logger.info("performing disconnect ($nsp)")
      packet(ClientPacket<Any>(DISCONNECT))
    }

    // remove socket from pool
    destroy()

    if (connected == true) {
      // fire events
      onclose("io client disconnect")
    }
    return this
  }

  /**
   * Disposes the socket manually which will destroy, close, disconnect the socket connection and
   * clear all the event listeners. Unlike [close] or [disconnect] which won"t clear all the event
   * listeners
   */
  fun dispose() {
    disconnect()
    clearListeners()
  }

  /**
   * Sets the compress flag.
   *
   * @param {Boolean} if `true`, compresses the sending data
   * @return {Socket} self
   * @api public
   */
  fun compress(compress: Boolean): SocketClient {
    flags["compress"] = compress
    return this
  }
}
