package io.dyte.socketio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement

typealias ACKFn = Function1<Any?, Unit>?

class SocketClient(val io: Manager, private val nsp: String, val opts: ManagerOptions) :
  EventEmitter() {

  companion object {
    const val EVENT_CONNECT = "connect"
    const val EVENT_DISCONNECT = "disconnect"
    private val EVENTS =
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
  }

  var ids = 0
  val acks = mutableMapOf<String, ((data: Any?) -> Unit)>()
  var connected = false
  var disconnected = true
  var sendBuffer = mutableListOf<ClientPacket>()
  var receiveBuffer = mutableListOf<MutableList<Any>>()
  val auth = opts.auth
  var subs = mutableListOf<Destroyable>()
  var flags = mutableMapOf<String, Boolean>()
  var id: String? = ""
  // TODO: DYTE
  //    if (io.autoConnect) open();

  /** Whether the Socket will try to reconnect when its Manager connects or reconnects */
  fun getActive(): Boolean {
    return subs.isEmpty()
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
    if (io.readyState == "open") onOpen(null)
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
      emitWithAck(
        event,
        data.toList().subList(0, data.size - 1),
        data.last() as Function1<Any?, Unit>
      )
    } else {
      emitWithAck(event, data.toList())
    }
  }

  override fun emit(event: String, data: Any?) {
    emitWithAck(event, data)
  }

  fun onConnect(callback: () -> Unit) {
    on(
      EVENT_CONNECT,
    ) {
      callback()
    }
  }

  fun onDisconnect(callback: () -> Unit) {
    on(
      EVENT_DISCONNECT,
    ) {
      callback()
    }
  }

  fun onEvent(event: String, callback: (data: ArrayList<JsonElement>) -> Unit) {
    onEvent(
      event,
    ) { data, _ ->
      callback(data)
    }
  }

  fun onEvent(event: String, callback: (data: ArrayList<JsonElement>, ack: ACKFn) -> Unit) {
    on(
      event,
    ) { data ->
      if (data is Function1<*, *>) {
        callback(ArrayList(), data as ACKFn)
      } else if (data is ArrayList<*>) {
        var ackElem: Function1<Any?, Unit>? = null
        try {
          val lastElem = data.last()
          if (lastElem is Function1<*, *>) {
            ackElem = lastElem as ACKFn
          }
        } catch (_: Exception) {}
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
  }

  fun emitWithAck(
    event: String,
    vararg data: Any?,
    ack: ((data: Any?) -> Unit)? = null,
  ) {
    emitWithAck(event, data.toList(), ack)
  }

  /**
   * Emits to this client.
   *
   * @return {Socket} self
   */
  fun emitWithAck(
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
  private fun onOpen(@Suppress("UNUSED_PARAMETER") data: Any?) {
    Logger.info("transport is open - connecting")

    if (auth != null) {
      packet(ClientPacket.Connect("", Json.encodeToJsonElement(auth) as JsonObject))
    } else {
      packet(ClientPacket.Connect())
    }
  }

  /** Called upon io.dyte.socketio.engine or manager `error` */
  private fun onError(err: Any?) {
    if (!connected) {
      emit("connect_error", err)
    }
  }

  /**
   * Called upon io.dyte.socketio.engine `close`.
   *
   * @param {String} reason
   */
  private fun onClose(data: Any?) {
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
  private fun onPacket(packet: Any?) {
    val clientPacket = packet as ClientPacket
    if (clientPacket.namespace != nsp) return
    Logger.debug("onPacket socket ${clientPacket}")
    when (clientPacket) {
      is ClientPacket.Connect -> {
        if (clientPacket.payload != null && clientPacket.payload["sid"] != null) {
          val id = clientPacket.payload["sid"].toString()
          onConnect(id)
        } else {
          emit(
            "connect_error",
            "It seems you are trying to reach a Socket.IO server in v2.x with a v3.x+ client, but they are not compatible (more information here: https://socket.io/docs/v3/migrating-from-2-x-to-3-0/)"
          )
        }
      }
      is ClientPacket.Event -> onEvent(clientPacket)
      is ClientPacket.BinaryEvent -> onEvent(clientPacket)
      is ClientPacket.Ack -> onAck(clientPacket)
      is ClientPacket.BinaryAck -> onAck(clientPacket)
      is ClientPacket.Disconnect -> onDisconnect()
      is ClientPacket.ConnectError -> emit("error", clientPacket.errorData)
    }
  }

  /** Subscribe to open, close and packet events */
  private fun subEvents() {
    if (subs.isNotEmpty()) return
    subs =
      mutableListOf(
        Destroyable.new(io, "open", ::onOpen),
        Destroyable.new(io, "packet", ::onPacket),
        Destroyable.new(io, "error", ::onError),
        Destroyable.new(io, "close", ::onClose)
      )
  }

  /**
   * Called upon a server event.
   *
   * @param {Object} packet
   */
  private fun onEvent(packet: ClientPacket.Message) {
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
    return { data: Any? ->
      // prevent double callbacks
      if (!sent) {
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
  }

  /**
   * Called upon a server acknowledgement.
   *
   * @param {Object} packet
   */
  private fun onAck(packet: ClientPacket.Message) {
    val ack = acks.remove("${packet.ackId}")
    if (ack is Function1<*, *>) {
      Logger.info("calling ack ${packet.ackId} with ${packet.payload}")

      val args = packet.payload
      ack(ArrayList(args.toList()))
    } else {
      Logger.warn("bad ack ${packet.ackId}")
    }
  }

  /** Called upon server connect. */
  private fun onConnect(id: String) {
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
  private fun onDisconnect() {
    Logger.warn("server disconnect ($nsp)")
    destroy()
    onClose("io server disconnect")
  }

  /**
   * Called upon forced client/server side disconnections, this method ensures the manager stops
   * tracking us and that reconnections don"t get triggered for this.
   */
  private fun destroy() {
    if (subs.isNotEmpty()) {
      val iterator = subs.iterator()
      while (iterator.hasNext()) {
        iterator.next().destroy()
        iterator.remove()
      }
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
    if (connected) {
      Logger.info("performing disconnect ($nsp)")
      packet(ClientPacket.Disconnect())
    }

    // remove socket from pool
    destroy()

    if (connected) {
      // fire events
      onClose("io client disconnect")
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
