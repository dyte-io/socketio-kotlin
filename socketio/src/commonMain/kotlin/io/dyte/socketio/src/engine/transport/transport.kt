import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.EnginePacket
import io.ktor.http.*

abstract class Transport: EventEmitter {
  lateinit var path: String
    var hostname: String
    var port: Int = -1
    var secure: Boolean = true
    var query: Parameters = Parameters.Empty
    var timestampParam: String?
    var timestampRequests: Boolean = false
    var extraHeaders: MutableMap<String, String>? = null

    var readyState: String?
    var agent: Boolean = false
    var socket: EngineSocket?
    var enablesXDR: Boolean?
    var writable: Boolean = true
    open var name: String = ""
    open var supportsBinary: Boolean = false
    var requestTimeout: Long = 2000L


    constructor(opts: TransportOptions, socket: EngineSocket?)
  {
    if(opts.path != null) path = opts.path!!
      if(opts.port != null) port = opts.port
      if(opts.secure != null) secure = opts.secure
      if(opts.query != null) query = opts.query!!
      if(opts.agent != null) agent = opts.agent!!
      if(opts.requestTimeout != null) requestTimeout = opts.requestTimeout!!
      hostname = opts.hostname.toString()
      timestampParam = opts.timestampParam
      timestampRequests = opts.timestampRequests
      readyState = ""
      extraHeaders = opts.extraHeaders

      this.socket = socket
      enablesXDR = opts.enablesXDR
  }

  companion object {
    val EVENT_RESPONSE_HEADERS = "responseHeaders"
  }

  /**
   * Emits an error.
   *
   * @param {String} str
   * @return {Transport} for chaining
   * @api public
  */
  fun onError(msg: String, desc: String) {
    emit("error" , mapOf<String, String>("msg" to msg, "desc" to desc, "type" to "TransportError"))
  }

  /**
   * Opens the transport.
   *
   * @api public
  */
  fun open() {
    Logger.info("Transport open() called $name")
    if ("closed" == readyState || "" == readyState) {
      Logger.debug("Opening transport $name")
      readyState = "opening"
        doOpen()
    }
  }

  /**
   * Closes the transport.
   *
   * @api private
  */
  fun close() {
    if ("opening" == readyState || "open" == readyState) {
      doClose()
        onClose()
    }
  }

  /**
   * Sends multiple packets.
   *
   * @param {Array} packets
   * @api private
  // TOOD List<*>
  */
  fun send(packets: List<EnginePacket>) {
    if ("open" == readyState) {
      write(packets)
    } else {
      throw IllegalStateException("Transport not open")
    }
  }

  /**
   * Called upon open
   *
   * @api private
  */
  fun onOpen() {
    readyState = "open"
      Logger.info("Transport open: readyState $name $readyState")
    writable = true
      emit("open")
  }

  /**
   * Called with data.
   *
   * @param {String} data
   * @api private
  */
  open fun onData(data: String) {
    Logger.debug("Transport  $name onData $data")
    var packet = EnginePacketParser.deserializePacket(data)
      onPacket(packet)
  }

  /**
   * Called with a decoded packet.
  */
  fun onPacket(packet: EnginePacket) {
    emit("packet", packet)
  }

  /**
   * Called upon close.
   *
   * @api private
  */
  fun onClose() {
    readyState = "closed"
    Logger.info("Transport $name closed. readyState $readyState ")
    emit("close")
  }

  abstract fun write(data: List<EnginePacket>)
    abstract fun doOpen()
    abstract fun doClose()
}

open class TransportOptions {
  var agent: Boolean? = null
    var hostname: String? = null
    var port: Int = -1
    var path: String? = null
    var secure: Boolean = false
    var query: Parameters? = null
    var timestampParam: String? = null
    var timestampRequests: Boolean = false

    var upgrade: Boolean? = true
    var forceJSONP: Boolean? = null
    var jsonp: Boolean? = null
    var forceBase64: Boolean? = null
    var enablesXDR: Boolean? = null
    var extraHeaders: MutableMap<String, String>? = null

    // TODO: ununsed ?
  var policyPort: Int = -1
    var requestTimeout: Long? = null
    var probe: Boolean? = null
    var autoConnect: Boolean? = null
}