import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.EnginePacket
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

class WebSocketTransport : Transport {

    override var name = "websocket"
//  var protocols: MutableList<String>?;

    override var supportsBinary: Boolean

    //  lateinit var perMessageDeflate: MutableMap<String, Any>;
    var ws: WebSocketSession? = null
    var scope: CoroutineScope? = null
    val client = HttpClient {
        install(WebSockets)
    }

    constructor(opts: TransportOptions, socket: EngineSocket? = null) : super(opts, socket) {
        var forceBase64 = opts.forceBase64 == true
        supportsBinary = !forceBase64
//    perMessageDeflate = opts["perMessageDeflate"] as MutableMap<String, Any>;
//    protocols = opts["protocols"] as MutableList<String>;
    }


    override fun doOpen() {
        val ur = this.uri()
//    var protocols = this.protocols;
        Logger.fine("WS:: ${this.uri()}")
        // TODO: DYTE
        scope = CoroutineScope(Dispatchers.Default)
        scope!!.launch {
            try {
                ws = client.webSocketSession(ur) {
                    headers {
                        append("Accept", "*/*")
                        extraHeaders?.forEach {
                            append(it.key, it.value as String)
                        }
                    }
                }
                onOpen()
                listen()
            } catch (err: Exception) {
                Logger.fine("WS:: Error $err")
                emit("error", err)
            }
        }


        // TODO: DYTE
//    if (ws.binaryType == null) {
//      supportsBinary = false;
//    }
//    ws!.binaryType = "arraybuffer";

        addEventListeners()
    }

    fun listen() {
        scope!!.launch {
            while (true) {
                try {


                    val frame = ws?.incoming?.receive()
                    if (frame is Frame.Text) {
                        val ft = frame.readText()
                        Logger.fine("WS:: recieved $ft")
                        onData(ft)
                    }
                } catch (e: Exception) {
                    Logger.fine("WS error $e")
                    onClose()
                    break
                }
            }
        }
    }

    /// Adds event listeners to the socket
    ///
    /// @api private
    fun addEventListeners() {
//    ws.incoming
//      ..onClose.listen((_) => onClose())
//      ..onMessage.listen((MessageEvent evt) => onData(evt.data))
//      ..onError.listen((e) {
//        onError("websocket error");
//      });
    }

    /// Writes data to socket.
    ///
    /// @param {Array} array of packets.
    /// @api private
    override fun write(packets: List<EnginePacket<Any>>) {
        writable = false

        var done = fun() {
            writable = true
            emit("drain")
        }

        var total = packets.size
        // encodePacket efficient as it uses WS framing
        // no need for encodePayload
        packets.forEach {
            val packet = it
            PacketParser.encodePacket(packet,
                supportsBinary, callback = fun(data) {
                    // Sometimes the websocket has already been closed but the browser didn"t
                    // have a chance of informing us about it yet, in that case send will
                    // throw an error
                    try {
                        scope!!.launch {

                            if(data is String) {
                                ws?.send(data)
                            } else if (data is ByteArray) {
                                ws?.send(data)
                            }
                        }
                    } catch (e: Error) {
                        Logger.fine("websocket closed before onclose event")
                    }

                    if (--total == 0) done()

                })
        }
    }

    /// Closes socket.
    ///
    /// @api private

    override fun doClose() {
        runBlocking {
            ws?.close()
        }
    }

    /// Generates uri for connection.
    ///
    /// @api private
    fun uri(): String {
        var query = this.query //TODO: or else
        var schema = if (secure) "wss" else "ws"
        var port = ""

        // afun port if default for schema
        if (this.port > 0 &&
            (("wss" == schema && this.port != 443) ||
                    ("ws" == schema && this.port != 80))
        ) {
            port = ":${this.port}"
        }

        // cache busting is forced
        if (timestampRequests) {
            query = query.plus(
                Parameters.build { append(timestampParam as String, GMTDate().timestamp.toString(36)); })
        }

        if (supportsBinary == false) {
            query = query.plus(
                Parameters.build { append("b64", "1") }
            )
        }


        var queryString = query.formUrlEncode()

        // prepend ? to query
        if (queryString.isNotEmpty()) {
            queryString = "?$queryString"
        }

        println("XX: $queryString")

        var ipv6 = hostname.contains(":");
        return schema +
                "://" +
                (if(ipv6) "[" + hostname + "]" else hostname) +
                port +
                path +
                queryString
    }
}
