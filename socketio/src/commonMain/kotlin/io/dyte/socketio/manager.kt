package io.dyte.socketio

import io.dyte.socketio.engine.EngineSocket
import io.dyte.socketio.engine.EngineSocketOptions
import io.dyte.socketio.engine.Timer
import kotlin.math.*
import kotlin.random.Random

/**
 * Manager` constructor.
 *
 * @param {String} io.dyte.socketio.engine instance or io.dyte.socketio.engine uri/opts
 * @param {ManagerOptions} options
 */
class Manager : EventEmitter {
  // Namespaces
  val nsps = mutableMapOf<String, SocketClient>()
  val subs = mutableListOf<Destroyable>()
  lateinit var options: ManagerOptions

  /**
   * Sets the `reconnection` config.
   *
   * @param {Boolean} true/false if it should automatically reconnect
   * @return {Manager} self or value
   */
  var reconnection = true

  /**
   * Sets the reconnection attempts config.
   *
   * @param {Number} max reconnection attempts before giving up
   * @return {Manager} self or value
   */
  var reconnectionAttempts = Int.MAX_VALUE

  /**
   * Sets the delay between reconnections.
   *
   * @param {Number} delay
   * @return {Manager} self or value
   */
  var reconnectionDelay = 1000L
  var randomizationFactor: Double = 0.5

  var reconnectionDelayMax = 5000L

  /**
   * Sets the connection timeout. `false` to disable
   *
   * @return {Manager} self or value
   */
  var timeout = 20000L
  var backoff: Backoff =
    Backoff(reconnectionDelay, reconnectionDelayMax, factor = randomizationFactor)
  var readyState: String = "closed"
  lateinit var uri: String
  var reconnecting = false

  lateinit var engine: EngineSocket

  var autoConnect: Boolean = true
  var skipReconnect: Boolean = false

  constructor(uri: String, options: ManagerOptions) {
    options.path = if (options.path != null) options.path else "/socket.io/"
    this.options = options
    reconnection = options.reconnection
    reconnectionAttempts = options.reconnectionAttempts
    reconnectionDelay = options.reconnectionDelay
    reconnectionDelayMax = options.reconnectionDelayMax
    randomizationFactor = options.randomizationFactor
    // TODO:
    //  fix Backoff
    //    backoff = Backoff(
    //        reconnectionDelay,
    //        reconnectionDelayMax,
    //        factor = randomizationFactor);
    backoff = Backoff()
    timeout = options.timeout
    this.uri = uri
    autoConnect = options.autoConnect != false
    if (autoConnect) open(opt = options)
  }

  /**
   * Sets the maximum delay between reconnections.
   *
   * @param {Number} delay
   * @return {Manager} self or value
   */
  companion object {
    val EVENT_RECONNECT_ATTEMPT = "reconnect_attempt"
    val EVENT_RECONNECT = "reconnect"
    val EVENT_RECONNECT_FAILED = "reconnect_failed"
    val EVENT_ERROR = "error"
  }

  fun setReconnectionDelayMaxInternal(v: Long) {
    reconnectionDelayMax = v
    backoff.max = v
  }

  /**
   * Starts trying to reconnect if reconnection is enabled and we have not started reconnecting yet
   */
  fun maybeReconnectOnOpen() {
    // Only try to reconnect if it"s the first time we"re connecting
    if (!reconnecting && reconnection == true && backoff.attempts == 0) {
      // keeps reconnection from firing twice for the same reconnection loop
      reconnect()
    }
  }

  /**
   * Sets the current transport `socket`.
   *
   * @param {Function} optional, callback
   * @return {Manager} self
   */
  fun open(callback: ((data: Any?) -> Unit)? = null, opt: EngineSocketOptions) {
    connect(callback = callback, opt = opt)
  }

  fun connect(callback: ((data: Any?) -> Unit)? = null, opt: EngineSocketOptions): Manager {
    Logger.info("Manager connect() readyState $readyState")
    if (readyState.contains("open")) return this

    Logger.debug("Manager opening $uri")
    engine = EngineSocket(uri, opt)
    var socket = engine
    readyState = "opening"
    skipReconnect = false

    // emit `open`
    var openSubDestroy =
      Util.on(
        socket,
        "open",
        fun(_) {
          onopen()
          if (callback != null) callback(null)
        }
      )

    // emit `connect_error`
    var errorSub =
      Util.on(
        socket,
        "error",
        fun(data) {
          Logger.error("Manager connect_error")
          cleanup()
          readyState = "closed"
          super.emit(EVENT_ERROR, data)
          if (callback != null) {
            callback(mutableMapOf("error" to "Connection error", "data" to data))
          } else {
            // Only do this if there is no fn to handle the error
            maybeReconnectOnOpen()
          }
        }
      )

    // emit `connect_timeout`
    if (timeout > -1) {
      Logger.info("connect attempt will timeout after $timeout")
      val timeoutFx =
        fun() {
          Logger.debug("connect attempt timed out after $timeout")
          openSubDestroy.destroy()
          socket.emit(EVENT_ERROR, "timeout")
          socket.close()
        }
      if (timeout == 0L) {
        // prevents a race condition with the "open" event
        timeoutFx()
        return this
      }
      // set timer
      var timer = Timer(timeout, timeoutFx)
      timer.schedule()

      subs.add(
        Destroyable(
          fun() {
            timer.cancel()
          }
        )
      )
    }

    subs.add(openSubDestroy)
    subs.add(errorSub)
    engine.open()
    return this
  }

  /** Called upon transport open. */
  fun onopen() {
    Logger.debug("Manager onopen")

    // clear old subs
    cleanup()

    // mark as open
    readyState = "open"
    emit("open")

    // add subs
    var socket = engine
    subs.add(Util.on(socket, "data", ::ondata))
    subs.add(Util.on(socket, "ping", ::onping))
    // subs.add(Util.on(socket, "pong", onpong));
    subs.add(Util.on(socket, "error", ::onerror))
    subs.add(Util.on(socket, "close", ::onclose))
  }

  /** Called upon a ping. */
  fun onping(data: Any? = null) {
    emit("ping")
  }

  /** Called with data. */
  fun ondata(data: Any?) {
    Logger.debug("Manager onData")
    if (data != null) {
      val packet = ClientParser.decode(data as String)
      emit("packet", packet)
    }
  }

  /** Called upon socket error. */
  fun onerror(err: Any?) {
    Logger.error("Manager error $err")
    emit(EVENT_ERROR, err)
  }

  /**
   * Creates a socket for the given `nsp`.
   *
   * @return {SocketClient}
   */
  fun socket(nsp: String): SocketClient {
    var socket = nsps[nsp]

    if (socket == null) {
      socket = SocketClient(this, nsp, this.options)
      nsps[nsp] = socket
    }

    return socket
  }

  /**
   * Called upon a socket close.
   *
   * @param SocketClient socket
   */
  fun destroy(socket: SocketClient) {
    var _nsps = nsps.keys

    for (nsp in _nsps) {
      val socket = nsps[nsp]

      if (socket?.getActive() == true) {
        Logger.warn("socket $nsp is still active, skipping close")
        // TODO: check if retun function or loop iteration
        return
      }
    }

    close()
  }

  /**
   * Writes a packet.
   *
   * @param {Object} packet
   */
  fun packet(packet: ClientPacket) {
    Logger.debug("writing packet ${packet}")

    var encodedPacket = ClientParser.encode(packet)
    engine.write(encodedPacket)
  }

  /** Clean up transport subscriptions and packet buffer. */
  fun cleanup() {
    Logger.debug("Manager cleanup")

    var subsLength = subs.size
    for (i in 1 until subsLength) {
      var sub = subs.removeAt(0)
      sub.destroy()
    }

    //    decoder.destroy();
  }

  /** Close the current socket. */
  fun close() {
    disconnect()
  }

  fun disconnect() {
    Logger.info("disconnect")
    skipReconnect = true
    reconnecting = false
    if ("open" != readyState) {
      // `onclose` will not fire because
      // an open event never happened
      cleanup()
    }
    backoff.reset()
    readyState = "closed"
    engine.close()
  }

  /** Called upon io.dyte.socketio.engine close. */
  fun onclose(error: Any?) {
    Logger.debug("onclose")

    cleanup()
    backoff.reset()
    readyState = "closed"
    //    emit("close", error.get("reason"));
    emit("close", "")

    if (reconnection && !skipReconnect) {
      reconnect()
    }
  }

  /** Attempt a reconnection. */
  fun reconnect(): Manager {
    if (reconnecting || skipReconnect) return this

    if (backoff.attempts >= reconnectionAttempts) {
      Logger.warn("reconnect failed")
      backoff.reset()
      emit(EVENT_RECONNECT_FAILED)
      reconnecting = false
    } else {
      var delay = backoff.duration
      Logger.info("will wait %dms before reconnect attempt $delay")

      reconnecting = true
      var timer =
        Timer(
          delay,
          fun() {
            Logger.debug("attempting reconnect 0")
            // TODO: RECHECK
            if (skipReconnect) return

            Logger.info("attempting reconnect")
            emit(EVENT_RECONNECT_ATTEMPT, backoff.attempts)

            // check again for the case socket closed in above events
            if (skipReconnect) return

            open(
              fun(err) {
                if (err != null) {
                  Logger.warn("reconnect attempt error")
                  reconnecting = false
                  reconnect()
                  //            emit("reconnect_error", err as MutableMap.get("data"));
                  emit("reconnect_error", "")
                } else {
                  Logger.info("reconnect success")
                  onreconnect()
                }
              },
              this.options
            )
          }
        )
      timer.schedule()

      subs.add(
        Destroyable(
          fun() {
            timer.cancel()
          }
        )
      )
    }
    return this
  }

  /** Called upon successful reconnect. */
  fun onreconnect() {
    var attempt = backoff.attempts
    reconnecting = false
    backoff.reset()
    emit(EVENT_RECONNECT, attempt)
  }
}

/**
 * Initialize backoff timer with `opts`.
 * - `min` initial timeout in milliseconds [100]
 * - `max` max timeout [10000]
 * - `jitter` [0]
 * - `factor` [2]
 *
 * @param {Object} opts
 */
class Backoff(min: Long = 100, max: Long = 10000, _jitter: Double = 0.0, factor: Double = 2.0) {
  var ms = min
  var max = max
  val factor = factor
  var _jitter = if (_jitter > 0 && _jitter <= 1) _jitter else 0.0
  var attempts = 0

  /**
   * Return the backoff duration.
   *
   * @return {Number}
   */
  val duration: Long
    get() {
      var _ms = min(ms * factor.pow(attempts++), 1e100)
      if (_jitter > 0.0) {
        var rand = Random(0).nextDouble()
        var deviation = floor(rand * _jitter * ms)
        _ms = if (((floor(rand * 10) as Int).and(1)) == 0) (ms - deviation) else (ms + deviation)
      }
      // #39: avoid an overflow with negative value
      if (max < +ms) {
        _ms = max.toDouble()
      }
      return if (ms <= 0) max else _ms.toLong()
    }

  /** Reset the number of attempts. */
  fun reset() {
    attempts = 0
  }

  /** Set the minimum duration */
  fun min(m: Long) {
    ms = m
  }

  /** Set the maximum duration */
  fun max(m: Long) {
    max = m
  }

  /** Set the jitter */
  fun jitter(jitter: Double) {
    if (jitter > 0 && jitter <= 1) {
      _jitter = jitter
    } else {
      throw IllegalArgumentException("jitter should be between 0.0 and 1.0")
    }
  }
}

open class ManagerOptions : EngineSocketOptions() {
  var reconnection = true
  var reconnectionAttempts = 5
  var reconnectionDelay: Long = 0
  var reconnectionDelayMax: Long = 0
  var randomizationFactor = 0.0
  var auth: Map<String, String>? = null

  /** Connection timeout (ms). Set -1 to disable. */
  var timeout: Long = 20000
}
