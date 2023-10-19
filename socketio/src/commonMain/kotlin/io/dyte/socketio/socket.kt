package io.dyte.socketio

import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

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
  var sendBuffer = mutableListOf<ClientPacket>()
  var receiveBuffer = mutableListOf<MutableList<Any>>()
  val query: Parameters? = opts.query
  val auth = opts.auth
  var subs: MutableList<Destroyable>? = mutableListOf<Destroyable>()
  var flags = mutableMapOf<String, Boolean>()
  var id: String? = ""
  // TODO: DYTE
  //    if (io.autoConnect) open();

  /** Whether the Socket will try to reconnect when its Manager connects or reconnects */
  fun getActive(): Boolean {
    return subs != null
  }

  /** "Opens" the socket. */
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
   */
  fun emit(event: String, vararg data: Any?) {
    if (data.isNotEmpty() && data.last() is Function1<*, *>) {
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
        data.forEach(sendData::addPrimitive)
      } else {
        sendData.addPrimitive(data)
      }

      val packet = ClientPacket.Event()
      packet.payload = buildJsonArray { sendData.forEach { add(it) } }

      // event ack callback
      if (ack != null) {
        Logger.info("emitting packet with ack id $ids")
        acks["$ids"] = ack
        packet.ackId = ids++
      }
      val isTransportWritable = io.engine.transport != null && io.engine.transport?.writable == true

      val discardPacket = flags.get("volatile") != null && (!isTransportWritable || !connected)
      if (discardPacket) {
        Logger.warn("discard packet as the transport is not currently writable")
      } else if (connected) {
        this.packet(packet)
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
  private fun packet(packet: ClientPacket) {
    packet.namespace = nsp
    io.packet(packet)
  }

  /** Called upon io.dyte.socketio.engine `open`. */
  private fun onopen(data: Any?) {
    Logger.info("transport is open - connecting")

    if (auth != null) {
      packet(ClientPacket.Connect("", Json.encodeToJsonElement(auth) as JsonObject))
    } else {
      packet(ClientPacket.Connect())
    }
  }

  /** Called upon io.dyte.socketio.engine or manager `error` */
  private fun onerror(err: Any?) {
    if (!connected) {
      emit("connect_error", err)
    }
  }

  /**
   * Called upon io.dyte.socketio.engine `close`.
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
    val packet = _packet as ClientPacket
    if (packet.namespace != nsp) return
    Logger.debug("onPacket socket ${packet}")
    when (packet) {
      is ClientPacket.Connect -> {
        if (packet.payload != null && packet.payload["sid"] != null) {
          val id = packet.payload["sid"].toString()
          onconnect(id)
        } else {
          emit(
            "connect_error",
            "It seems you are trying to reach a Socket.IO server in v2.x with a v3.x+ client, but they are not compatible (more information here: https://socket.io/docs/v3/migrating-from-2-x-to-3-0/)"
          )
        }
      }
      is ClientPacket.Event -> onevent(packet)
      is ClientPacket.BinaryEvent -> onevent(packet)
      is ClientPacket.Ack -> onack(packet)
      is ClientPacket.BinaryAck -> onack(packet)
      is ClientPacket.Disconnect -> ondisconnect()
      is ClientPacket.ConnectError -> emit("error", packet.errorData)
    }
  }

  /** Subscribe to open, close and packet events */
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
  private fun onevent(packet: ClientPacket.Message) {
    val args = packet.payload.toMutableList<Any>()

    packet.ackId?.let { args.add(ack(it)) }

    if (connected) {
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

  /** Produces an ack callback to emit with an event. */
  private fun ack(id: Int): (Any) -> Unit {
    var sent = false
    return fun(data: Any?) {
      // prevent double callbacks
      if (sent) return
      sent = true
      Logger.info("sending ack $data")

      val sendData = mutableListOf<JsonElement>()
      if (data is List<*>) {
        data.forEach(sendData::addPrimitive)
      } else {
        sendData.addPrimitive(data)
      }
      val sendDataJson = buildJsonArray { sendData.forEach { add(it) } }
      val p = ClientPacket.Ack("", id, sendDataJson)
      packet(p)
    }
  }

  /**
   * Called upon a server acknowledgement.
   *
   * @param {Object} packet
   */
  private fun onack(packet: ClientPacket.Message) {
    val ack_ = acks.remove("${packet.ackId}")
    if (ack_ is Function1<*, *>) {
      Logger.info("calling ack ${packet.ackId} with ${packet.payload}")

      val args = packet.payload
      ack_(ArrayList(args.toList()))
    } else {
      Logger.warn("bad ack ${packet.ackId}")
    }
  }

  /** Called upon server connect. */
  private fun onconnect(id: String) {
    this.id = id
    connected = true
    disconnected = false
    emit("connect")
    emitBuffered()
  }

  /** Emit buffered events (received and emitted). */
  private fun emitBuffered() {
    Logger.debug("Emitting buffered")
    receiveBuffer.forEach { args ->
      super.emit(
        args.first().toString().removePrefix("\"").removeSuffix("\""),
        ArrayList(args.subList(1, args.size))
      )
    }
    receiveBuffer.clear()

    sendBuffer.forEach { packet(it) }
    sendBuffer.clear()
  }

  /** Called upon server disconnect. */
  private fun ondisconnect() {
    Logger.warn("server disconnect ($nsp)")
    destroy()
    onclose("io server disconnect")
  }

  /**
   * Called upon forced client/server side disconnections, this method ensures the manager stops
   * tracking us and that reconnections don"t get triggered for this.
   */
  private fun destroy() {
    val _subs = subs
    if (!_subs.isNullOrEmpty()) {
      // clean subscriptions to avoid reconnections

      for (i in 0 until _subs.size) {
        _subs[i].destroy()
      }
      subs = null
    }

    io.destroy()
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
      packet(ClientPacket.Disconnect())
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
   * @return {SocketClient} self
   */
  fun compress(compress: Boolean): SocketClient {
    flags["compress"] = compress
    return this
  }
}

private fun MutableList<JsonElement>.addPrimitive(primitive: Any?) {
  when (primitive) {
    is String -> {
      add(JsonPrimitive(primitive))
    }
    is Boolean -> {
      add(JsonPrimitive(primitive))
    }
    is Number -> {
      add(JsonPrimitive(primitive))
    }
    is JsonElement -> {
      add(primitive)
    }
  }
}