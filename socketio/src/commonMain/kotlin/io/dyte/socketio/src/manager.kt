import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.ClientPacket
import io.dyte.socketio.src.engine.Timer
import kotlin.math.*
import kotlin.random.Random

/**
  * Manager` constructor.
  * @param {String} engine instance or engine uri/opts
  * @param {ManagerOptions} options
*/
class Manager: EventEmitter {
  // Namespaces
  val nsps = mutableMapOf<String, SocketClient>();
  val subs = mutableListOf<Destroyable>();
  lateinit var options: ManagerOptions;

  /**
    * Sets the `reconnection` config.
    *
    * @param {Boolean} true/false if it should automatically reconnect
    * @return {Manager} self or value
    * @api public
  */
  var reconnection = true;

  /**
    * Sets the reconnection attempts config.
    *
    * @param {Number} max reconnection attempts before giving up
    * @return {Manager} self or value
    * @api public
  */
  var reconnectionAttempts = Int.MAX_VALUE;

  /**
    * Sets the delay between reconnections.
    *
    * @param {Number} delay
    * @return {Manager} self or value
    * @api public
  */
  var reconnectionDelay = 1000L;
  var randomizationFactor: Double = 0.5;

  var reconnectionDelayMax = 5000L;

  /**
    * Sets the connection timeout. `false` to disable
    *
    * @return {Manager} self or value
    * @api public
  */
  var timeout = 20000L;
  var backoff: Backoff = Backoff(reconnectionDelay,reconnectionDelayMax, factor = randomizationFactor);
  var readyState: String = "closed";
  lateinit var uri: String;
  var reconnecting = false;

  lateinit var engine: EngineSocket;
  var encoder = ClientEncoder();
  var decoder = ClientDecoder();
  var autoConnect: Boolean = true;
  var skipReconnect: Boolean = false;

  constructor(uri: String ,options: ManagerOptions) {
    options.path =  if(options.path != null) options.path else "/socket.io/";
    this.options = options;
    reconnection = options.reconnection;
    reconnectionAttempts = options.reconnectionAttempts;
    reconnectionDelay = options.reconnectionDelay;
    reconnectionDelayMax = options.reconnectionDelayMax;
    randomizationFactor = options.randomizationFactor;
    backoff = Backoff(
        reconnectionDelay,
        reconnectionDelayMax,
        factor = randomizationFactor);
    timeout = options.timeout;
    this.uri = uri;
    autoConnect = options.autoConnect != false;
    if (autoConnect) open(opt=options);
  }
  /**
    * Sets the maximum delay between reconnections.
    *
    * @param {Number} delay
    * @return {Manager} self or value
    * @api public
  */
  fun setReconnectionDelayMaxInternal(v : Long) {
    reconnectionDelayMax = v;
    backoff?.max = v;
  }

  /**
    * Starts trying to reconnect if reconnection is enabled and we have not
    * started reconnecting yet
    *
    * @api private
  */
  fun maybeReconnectOnOpen() {
    // Only try to reconnect if it"s the first time we"re connecting
    if (!reconnecting && reconnection == true && backoff.attempts == 0) {
      // keeps reconnection from firing twice for the same reconnection loop
      reconnect();
    }
  }

  /**
    * Sets the current transport `socket`.
    *
    * @param {Function} optional, callback
    * @return {Manager} self
    * @api public
  */
  fun open(callback: ((data: Any?) -> Unit)? = null, opt: EngingeSocketOptions) {
    connect(callback=callback, opt=opt);
  }

  fun connect(callback: ((data: Any?) -> Unit)? = null, opt: EngingeSocketOptions): Manager {
    Logger.fine("readyState $readyState");
    if (readyState.contains("open")) return this;

    Logger.fine("opening $uri");
    engine = EngineSocket(uri, opt);
    var socket = engine;
    readyState = "opening";
    skipReconnect = false;

    // emit `open`
    var openSubDestroy = Util.on(socket, "open", fun (data: Any?) {
      onopen();
      if (callback != null) callback(null);
    });

    // emit `connect_error`
    var errorSub = Util.on(socket, "error", fun (data) {
      Logger.fine("connect_error");
      cleanup();
      readyState = "closed";
      super.emit("error", data);
      if (callback != null) {
        callback(mutableMapOf("error" to "Connection error", "data" to data));
      } else {
        // Only do this if there is no fn to handle the error
        maybeReconnectOnOpen();
      }
    });

    // emit `connect_timeout`
    if (timeout != null) {
      Logger.fine("connect attempt will timeout after $timeout");

      if (timeout == 0L) {
        openSubDestroy
            .destroy(); // prevents a race condition with the "open" event
      }
      // set timer
      var timer = Timer(timeout.toLong(), fun () {
        Logger.fine("connect attempt timed out after $timeout");
        openSubDestroy.destroy();
        socket.close();
        socket.emit("error", "timeout");
      });

      subs.add(Destroyable(fun() { timer.cancel()}));
    }

    subs.add(openSubDestroy);
    subs.add(errorSub);
    engine.open()
    return this;
  }

  /**
    * Called upon transport open.
    *
    * @api private
  */
  fun onopen() {
    Logger.fine("open");

    // clear old subs
    cleanup();

    // mark as open
    readyState = "open";
    emit("open");

    // add subs
    var socket = engine;
    subs.add(Util.on(socket, "data", ::ondata));
    subs.add(Util.on(socket, "ping", ::onping));
    // subs.add(Util.on(socket, "pong", onpong));
    subs.add(Util.on(socket, "error", ::onerror));
    subs.add(Util.on(socket, "close", ::onclose));
    subs.add(Util.on(decoder, "decoded", ::ondecoded));
  }

  /**
    * Called upon a ping.
    *
    * @api private
  */
  fun onping(data: Any? = null) {
    emit("ping");
  }

  /**
    * Called upon a packet.
    *
    * @api private
  */
//   fun onpong([_]) {
//     emitAll("pong", DateTime.now().millisecondsSinceEpoch - lastPing);
//   }

  /**
    * Called with data.
    *
    * @api private
  */
  fun ondata(data: Any?) {
    Logger.fine("Manager onData0");
    if (data != null) {
      Logger.fine("Manager onData1");
      decoder.add(data)
    };
  }

  /**
    * Called when parser fully decodes a packet.
    *
    * @api private
  */
  fun ondecoded(packet: Any?) {
    Logger.fine("Manager onDecoded");
    emit("packet", packet);
  }

  /**
    * Called upon socket error.
    *
    * @api private
  */
  fun onerror(err: Any?) {
    Logger.fine("error $err");
    emit("error", err);
  }

  /**
    * Creates a socket for the given `nsp`.
    *
    * @return {Socket}
    * @api public
  */
  fun socket(nsp: String, opts: ManagerOptions): SocketClient {
    var socket = nsps[nsp];

    if (socket == null) {
      socket = SocketClient(this, nsp, opts);
      nsps[nsp] = socket;
    }

    return socket;
  }

  /**
    * Called upon a socket close.
    *
    * @param SocketClient socket
  */
  fun destroy(socket: SocketClient) {
    var _nsps = nsps.keys;

    for (nsp in _nsps) {
      val socket = nsps[nsp];

      if (socket?.getActive() == true) {
        Logger.fine("socket $nsp is still active, skipping close");
        // TODO: check if retun function or loop iteration
        return;
      }
    }

    close();
  }

  /**
    * Writes a packet.
    *
    * @param {Object} packet
    * @api private
  */
  fun packet(packet: ClientPacket<*>) {
    Logger.fine("writing packet $packet");

    // if (encoding != true) {
    // encode, then write to engine with result
    // encoding = true;
    var encodedPackets = encoder.encode(packet);

    for (i in 0..(encodedPackets.size - 1)) {
      engine.write(encodedPackets[i] as String);
    }
    // } else {
    // add packet to the queue
    // packetBuffer.add(packet);
    // }
  }

  /**
    * Clean up transport subscriptions and packet buffer.
    *
    * @api private
  */
  fun cleanup() {
    Logger.fine("cleanup");

    var subsLength = subs.size;
    for (i in 1..(subsLength-1)) {
      var sub = subs.removeAt(0);
      sub.destroy();
    }

//    decoder.destroy();
  }

  /**
    * Close the current socket.
    *
    * @api private
  */
  fun close() {
    disconnect();
  }

  fun disconnect() {
    Logger.fine("disconnect");
    skipReconnect = true;
    reconnecting = false;
    if ("opening" == readyState) {
      // `onclose` will not fire because
      // an open event never happened
      cleanup();
    }
    backoff.reset();
    readyState = "closed";
    engine?.close();
  }

  /**
    * Called upon engine close.
    *
    * @api private
  */
  fun onclose(error: Any?) {
    Logger.fine("onclose");

    cleanup();
    backoff.reset();
    readyState = "closed";
//    emit("close", error.get("reason"));
    emit("close", "");

    if (reconnection == true && !skipReconnect) {
      reconnect();
    }
  }

  /**
    * Attempt a reconnection.
    *
    * @api private
  */
  fun reconnect(): Manager {
    if (reconnecting || skipReconnect) return this;

    if (backoff.attempts >= reconnectionAttempts) {
      Logger.fine("reconnect failed");
      backoff.reset();
      emit("reconnect_failed");
      reconnecting = false;
    } else {
      var delay = backoff.duration;
      Logger.fine("will wait %dms before reconnect attempt $delay");

      reconnecting = true;
      var timer = Timer( delay.toLong(), fun () {
        // TODO: RECHECK
        if (!skipReconnect) return;

        Logger.fine("attempting reconnect");
        emit("reconnect_attempt", backoff.attempts);

        // check again for the case socket closed in above events
        if (skipReconnect) return;

        open(fun (err)  {
          if (err != null) {
            Logger.fine("reconnect attempt error");
            reconnecting = false;
            reconnect();
//            emit("reconnect_error", err as MutableMap.get("data"));
            emit("reconnect_error", "");
          } else {
            Logger.fine("reconnect success");
            onreconnect();
          }
        }, EngingeSocketOptions());
      });

      subs.add(Destroyable(fun () { timer.cancel()}));
    }
    return this;
  }

  /**
    *
    * Called upon successful reconnect.
    *
    * @api private
    *
   */
  fun onreconnect() {
    var attempt = backoff.attempts;
    reconnecting = false;
    backoff.reset();
    emit("reconnect", attempt);
  }
}

/**
  * Initialize backoff timer with `opts`.
  *
  * - `min` initial timeout in milliseconds [100]
  * - `max` max timeout [10000]
  * - `jitter` [0]
  * - `factor` [2]
  *
  * @param {Object} opts
  * @api public
*/
class Backoff(min: Long = 100, max: Long = 10000, _jitter: Double = 0.0, factor: Double = 2.0) {
  var ms = min;
  var max = max;
  val factor = factor;
  var _jitter = if(_jitter > 0 && _jitter <= 1) _jitter else 0.0;
  var attempts = 0;

  /**
    * Return the backoff duration.
    *
    * @return {Number}
    * @api public
  */
  val duration: Long
    get() {
    var _ms = min(ms * factor.pow(attempts++), 1e100);
    if (_jitter > 0.0) {
      var rand = Random(0).nextDouble();
      var deviation = floor(rand * _jitter * ms);
      _ms = if(((floor(rand * 10) as Int).and(1)) == 0) (ms - deviation) else (ms + deviation);
    }
    // #39: avoid an overflow with negative value
    if(max < +ms){
      _ms = max.toDouble();
    }
    return if(ms <= 0) max else _ms.toLong();
  }

  /**
    * Reset the number of attempts.
    *
    * @api public
  */
  fun reset() {
    attempts = 0;
  }

  /**
    * Set the minimum duration
    *
    * @api public
  */
  fun min(m: Long) { ms = m };

  /**
    * Set the maximum duration
    *
    * @api public
  */
  fun max(m: Long) { max = m };

  /**
    * Set the jitter
    *
    * @api public
  */
  fun jitter(jitter: Double) {
    if(jitter > 0 && jitter <= 1) {
      _jitter = jitter
    } else {
      throw IllegalArgumentException("jitter should be between 0.0 and 1.0")
    }
  };
}

open class ManagerOptions : EngingeSocketOptions() {
  var reconnection = true
  var reconnectionAttempts = 0
  var reconnectionDelay: Long = 0
  var reconnectionDelayMax: Long = 0
  var randomizationFactor = 0.0
  var auth: Map<String, String>? = null

  /**
   * Connection timeout (ms). Set -1 to disable.
   */
  var timeout: Long = 20000
}
