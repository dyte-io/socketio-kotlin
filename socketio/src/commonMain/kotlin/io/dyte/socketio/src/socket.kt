import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.ClientPacket
import io.ktor.http.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

typealias ACKFn = Function1<Any?, Unit>?;
class SocketClient(io: Manager, nsp: String, opts: ManagerOptions): EventEmitter() {
    var CONNECT = 0;
    var DISCONNECT = 1;
    var EVENT = 2;
    var ACK = 3;
    var CONNECT_ERROR = 4;
    var BINARY_EVENT = 5;
    var BINARY_ACK = 6;

    var EVENTS = listOf<String>(
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
    );

    companion object {
        val EVENT_CONNECT = "connect";
    }

    val nsp: String = nsp;
    val opts: ManagerOptions = opts;

    val io: Manager = io;
    val json: SocketClient = this;
    var ids = 0;
    val acks: MutableMap<String, ((data: Any?) -> Unit)> = mutableMapOf<String,((data: Any?) -> Unit)>();
    var connected = false;
    var disconnected = true;
    var sendBuffer = mutableListOf<ClientPacket<*>>();
    var receiveBuffer = mutableListOf<MutableList<Any>>();
    val query: Parameters? = opts.query;
    val auth: Any? = opts.auth;
    var subs: MutableList<Destroyable>? = mutableListOf<Destroyable>();
    var flags = mutableMapOf<String, Boolean>();
    var id: String? = "";
    // TODO: DYTE
//    if (io.autoConnect) open();

    /**
     *     Whether the Socket will try to reconnect when its Manager connects or reconnects
     */
    fun getActive(): Boolean
    {
        return subs != null;
    }

    /**
      * "Opens" the socket.
      *
      * @api public
     */
    fun open() {
        connect()
    };

    fun connect(): SocketClient {
        if (connected) return this;
        subEvents();
        if (!io.reconnecting) {
            io.open(opt=EngingeSocketOptions()); // ensure open
        }
        if (io.readyState == "open") onopen(null);
        return this;
    }

    /**
      * Sends a `message` event.
      *
      * @return {Socket} self
      * @api public
    */
    fun send(args: List<Any>): SocketClient {
        emit("message", args);
        return this;
    }

    /**
      * Override `emit`.
      * If the event is in `events`, it"s emitted normally.
      *
      * @param {String} event name
      * @return {Socket} self
      * @api public
    */
    fun emit(event: String, vararg data: Any?) {
        if(data.size > 0 && data.last() is Function1<*,*>){
            _emitWithAck(event, data.toList().subList(0,data.size - 1), data.last() as Function1<Any?,Unit>);
        } else {
            _emitWithAck(event, data.toList())
        }
    }

    override fun emit(event: String, data: Any?) {
        _emitWithAck(event, data);
    }

    fun onConnect(callback: () -> Unit) {
        on("connect", fun (_) {
            callback();
        });
    }

    fun onEvent(event: String, callback: (data: ArrayList<JsonElement>) -> Unit) {
        onEvent(event, fun (data: ArrayList<JsonElement>, ack: ACKFn) {
            callback(data);
        });
    }

    fun onEvent(event: String, callback: (data: ArrayList<JsonElement>, ack: ACKFn) -> Unit) {
        on(event, fun (data: Any?) {
            if(data is Function1<*, *>) {
                callback(ArrayList(), data as ACKFn);
            } else if (data is ArrayList<*>) {
                var ackElem: Function1<Any?, Unit>? =  null
                try {
                    val lastElem = data.last()
                    if(lastElem is Function1<*,*>) {
                        ackElem = data.last() as ACKFn;
                    }
                }catch (e: Exception) {}
                val _data = data as ArrayList<JsonElement>;
                if(ackElem != null) {
                    // all data except the ack callback
                    callback(ArrayList(_data.subList(0, data.size -1)), ackElem);
                } else {
                    callback(_data, null)
                }
            }
        });
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
        event: String, data: Any? = null,
        ack: ((data: Any?) -> Unit)? = null,
    ) {
        if (EVENTS.contains(event)) {
            super.emit(event, data);
        } else {
            var sendData = mutableListOf<JsonElement>(JsonPrimitive(event));
            if (data is List<*>) {
                data.forEach {
                    if(it is String) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is Boolean) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is Number) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is JsonElement) {
                        sendData.add(it);
                    }
                }
            } else if (data is JsonElement) {
                sendData.add(data);
            }

            var packet = ClientPacket(EVENT,sendData)
//                "options" to mutableMapOf<String, Boolean>("compress" to flags.getOrElse("compress") { "false "} as Boolean)

            // event ack callback
            if (ack != null) {
                Logger.fine("emitting packet with ack id $ids");
                acks["$ids"] = ack;
                packet.id = ids++;
            }
            val isTransportWritable = io.engine != null && io.engine.transport != null && io.engine.transport?.writable == true;

            val discardPacket = flags.get("volatile") != null && (!isTransportWritable || !connected);
            if (discardPacket) {
                Logger
                    .fine("discard packet as the transport is not currently writable");
            } else if (connected) {
                this.packet(packet as ClientPacket<*>);
            } else {
                sendBuffer.add(packet);
            }
            flags.clear(); // TODO: Recheck
        }
    }

    /**
      * Sends a packet.
      *
      * @param {Object} packet
      * @api private
    */
    fun packet(packet: ClientPacket<*>)
    {
        packet.nsp = nsp;
        io.packet(packet);
    }

    /**
      * Called upon engine `open`.
      *
      * @api private
    */
    fun onopen(data: Any?)
    {
        Logger.fine("transport is open - connecting");

        // write connect packet if necessary
        // if ("/" != nsp) {
        // if (query?.isNotEmpty == true) {
        //   packet({"type": CONNECT, "query": query});
        // } else {
        // packet({"type": CONNECT});
        // }
        // }

        if (auth != null) {
            if (auth is Function1<*, *>) {
                (auth as Function1<Any,Unit>).invoke(fun (data: Any) {
                    packet(ClientPacket(CONNECT, data ));
                });
            } else {
                packet(ClientPacket(CONNECT, auth ));
            }
        } else {
            packet(ClientPacket<Any>(CONNECT));
        }
    }

    /** 
     * Called upon engine or manager `error`
     */
    fun onerror(err: Any?)
    {
        if (!connected) {
            emit("connect_error", err);
        }
    }

    /**
      * Called upon engine `close`.
      *
      * @param {String} reason
      * @api private
    */
    fun onclose(data: Any?)
    {
        val reason = data as String;
        Logger.fine("close ($reason)");
        emit("disconnecting", reason);
        connected = false;
        disconnected = true;
        id = null;
        emit("disconnect", reason);
    }

    /**
      * Called with socket packet.
      *
      * @param {Object} packet
      * @api private
    */
    fun onpacket(_packet: Any?)
    {
        val packet = _packet as ClientPacket<*>
        if (packet.nsp != nsp) return;
        Logger.fine("onPacket socket ${packet.type}")
        when(packet.type) {
            CONNECT -> {
                if (packet.data != null && (packet.data as JsonObject)["sid"] != null) {
                    val id = (packet.data as JsonObject)["sid"].toString();
                    onconnect(id);
                } else {
                    emit(
                        "connect_error",
                        "It seems you are trying to reach a Socket.IO server in v2.x with a v3.x client, but they are not compatible (more information here: https://socket.io/docs/v3/migrating-from-2-x-to-3-0/)"
                    );
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
      * @api private
    */
    fun subEvents() {
        if (subs?.isNotEmpty() == true) return;
        var io = this.io;
        subs = mutableListOf(
            Util.on(io, "open", ::onopen),
            Util.on(io, "packet", ::onpacket),
            Util.on(io, "error", ::onerror),
            Util.on(io, "close", ::onclose)
        );
    }

    /**
      * Called upon a server event.
      *
      * @param {Object} packet
      * @api private
    */
    fun onevent(packet: ClientPacket<*>)
    {
        val args = ((packet.data ?: buildJsonArray {  }) as JsonArray).toMutableList<Any>();
//    debug("emitting event %j", args);

        if (null != packet.id) {
//      debug("attaching ack callback to event");
            args.add(ack(packet.id));
        }
        Logger.fine("onEvent size ${args.size} $connected");
        args.forEach {
            Logger.fine("onEvent ${it}");
        }
        if (connected == true) {
            try {
                super.emit(args.first().toString().removePrefix("\"").removeSuffix("\""), ArrayList(args.subList(1, args.size)));
            } catch (e: Exception){Logger.fine("args socket error emit $e")}
        } else {
            receiveBuffer.add(args);
        }
    }

    /**
      * Produces an ack callback to emit with an event.
      *
      * @api private
    */
    fun ack(id: Int): (Any) -> Unit {
        var sent = false;
        return fun (data: Any?) {
            // prevent double callbacks
            if (sent) return;
            sent = true;
            Logger.fine("sending ack $data");

            var sendData = mutableListOf<JsonElement>();
            if (data is List<*>) {
                data.forEach {
                    if(it is String) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is Boolean) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is Number) {
                        sendData.add(JsonPrimitive(it));
                    } else if (it is JsonElement) {
                        sendData.add(it);
                    }
                }
            }
            val p = ClientPacket( ACK, sendData );
            p.id = id;
            packet(p);
        };
    }

    /**
      * Called upon a server acknowlegement.
      *
      * @param {Object} packet
      * @api private
    */
    fun onack(packet: ClientPacket<*>)
    {
        var ack_ = acks.remove("${packet.id}");
        if (ack_ is Function1<*,*>) {
            Logger.fine("calling ack ${packet.id} with ${packet.data}");

            var args = packet.data as JsonArray;
            ack_(ArrayList(args.toList()));
        } else {
            Logger.fine("bad ack ${packet.id}");
        }
    }

    /**
      * Called upon server connect.
      *
      * @api private
    */
    fun onconnect(id: String)
    {
        this.id = id;
        connected = true;
        disconnected = false;
        emit("connect");
        emitBuffered();
    }

    /**
      * Emit buffered events (received and emitted).
      *
      * @api private
    */
    fun emitBuffered()
    {
        for (i in 0..(receiveBuffer.size-1)) {
        val args = receiveBuffer[i];
        if (args.size > 2) {
            super.emit(args.first() as String, args.subList(1,args.size));
        } else {
           super.emit(args.first() as String,null);
        }
    }
        receiveBuffer = mutableListOf<MutableList<Any>>();

        for (i in 0..(sendBuffer.size-1)) {
            packet(sendBuffer[i]);
        }
        sendBuffer = mutableListOf();
    }

    /**
      * Called upon server disconnect.
      *
      * @api private
    */
    fun ondisconnect()
    {
        Logger.fine("server disconnect ($nsp)");
        destroy();
        onclose("io server disconnect");
    }

    /**
      * Called upon forced client/server side disconnections,
      * this method ensures the manager stops tracking us and
      * that reconnections don"t get triggered for this.
      *
      * @api private.
    */
    fun destroy()
    {
        val _subs = subs;
        if (_subs != null && _subs.isNotEmpty()) {
            // clean subscriptions to avoid reconnections

            for (i in 0..(_subs.size-1)) {
                _subs[i].destroy();
            }
            subs = null;
        }

        io.destroy(this);
    }

    /**
      * Disconnects the socket manually.
      *
      * @return {Socket} self
      * @api public
    */
    fun close(): SocketClient {
        return disconnect();
    };

    fun disconnect(): SocketClient
    {
        if (connected == true) {
            Logger.fine("performing disconnect ($nsp)");
            packet(ClientPacket<Any>(DISCONNECT));
        }

        // remove socket from pool
        destroy();

        if (connected == true) {
            // fire events
            onclose("io client disconnect");
        }
        return this;
    }

    /**
      * Disposes the socket manually which will destroy, close, disconnect the socket connection
      * and clear all the event listeners. Unlike [close] or [disconnect] which won"t clear
      * all the event listeners
    */
    fun dispose()
    {
        disconnect();
        clearListeners();
    }

    /**
      * Sets the compress flag.
      *
      * @param {Boolean} if `true`, compresses the sending data
      * @return {Socket} self
      * @api public
    */
    fun compress(compress: Boolean): SocketClient
    {
        flags["compress"] = compress;
        return this;
    }
}
