package io.dyte.socketio

import io.dyte.socketio.engine.EngineSocket
import io.dyte.socketio.engine.EngineSocketOptions
import io.dyte.socketio.engine.Timer
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

// TODO:
//  fix Backoff
//    backoff = Backoff(
//        reconnectionDelay,
//        reconnectionDelayMax,
//        factor = randomizationFactor);
class Manager(private var uri: String, private var options: ManagerOptions) : EventEmitter() {
  // Namespaces
  val nsps = mutableMapOf<String, SocketClient>()
  val subs = mutableListOf<Destroyable>()

  /** Sets the `reconnection` config. */
  var reconnection = true

  /** Sets the reconnection attempts config. */
  var reconnectionAttempts = Int.MAX_VALUE

  /** Sets the delay between reconnections. */
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
  var reconnecting = false

  lateinit var engine: EngineSocket

  var autoConnect: Boolean = true
  var skipReconnect: Boolean = false

  init {
    options.path = if (options.path != null) options.path else "/socket.io/"
    reconnection = options.reconnection
    reconnectionAttempts = options.reconnectionAttempts
    reconnectionDelay = options.reconnectionDelay
    reconnectionDelayMax = options.reconnectionDelayMax
    randomizationFactor = options.randomizationFactor
    backoff = Backoff()
    timeout = options.timeout
    autoConnect = options.autoConnect != false
    if (autoConnect) open(opt = options)
  }

  companion object {
    const val EVENT_RECONNECT_ATTEMPT = "reconnect_attempt"
    const val EVENT_RECONNECT = "reconnect"
    const val EVENT_RECONNECT_FAILED = "reconnect_failed"
    const val EVENT_ERROR = "error"
  }

  /**
   * Sets the maximum delay between reconnections.
   *
   * @param delay Delay in milliseconds
   */
  fun setReconnectionDelayMaxInternal(delay: Long) {
    reconnectionDelayMax = delay
    backoff.max(delay)
  }

  /**
   * Starts trying to reconnect if reconnection is enabled and we have not started reconnecting yet
   */
  fun maybeReconnectOnOpen() {
    // Only try to reconnect if it"s the first time we"re connecting
    if (!reconnecting && reconnection && backoff.attempts == 0) {
      // keeps reconnection from firing twice for the same reconnection loop
      reconnect()
    }
  }

  /**
   * Sets the current transport `socket`.
   *
   * @param callback optional method invoked with the error if any
   */
  fun open(callback: ((data: Any?) -> Unit)? = null, opt: EngineSocketOptions) {
    connect(callback = callback, opt = opt)
  }

  fun connect(callback: ((data: Any?) -> Unit)? = null, opt: EngineSocketOptions): Manager {
    Logger.info("Manager connect() readyState $readyState")
    if (readyState.contains("open")) return this

    Logger.debug("Manager opening $uri")
    engine = EngineSocket(uri, opt)
    readyState = "opening"
    skipReconnect = false

    // emit `open`
    val openSubDestroy =
      Util.on(
        engine,
        "open",
        fun(_) {
          onOpen()
          if (callback != null) callback(null)
        }
      )

    // emit `connect_error`
    val errorSub =
      Util.on(
        engine,
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
          engine.emit(EVENT_ERROR, "timeout")
          engine.close()
        }
      if (timeout == 0L) {
        // prevents a race condition with the "open" event
        timeoutFx()
        return this
      }
      // set timer
      val timer = Timer(timeout, timeoutFx)
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
  fun onOpen() {
    Logger.debug("Manager onopen")

    // clear old subs
    cleanup()

    // mark as open
    readyState = "open"
    emit("open")

    // add subs
    subs.add(Util.on(engine, "data", ::onData))
    subs.add(Util.on(engine, "ping", ::onPing))
    // subs.add(Util.on(engine, "pong", onPong));
    subs.add(Util.on(engine, "error", ::onError))
    subs.add(Util.on(engine, "close", ::onClose))
  }

  /** Called upon a ping. */
  fun onPing(data: Any? = null) {
    emit("ping:: $data")
  }

  /** Called with data. */
  fun onData(data: Any?) {
    Logger.debug("Manager onData")
    if (data != null) {
      val packet = ClientParser.decode(data as String)
      emit("packet", packet)
    }
  }

  /** Called upon socket error. */
  fun onError(err: Any?) {
    Logger.error("Manager error $err")
    emit(EVENT_ERROR, err)
  }

  /**
   * Creates a socket for the given `nsp`.
   *
   * @return The created [SocketClient] instance.
   */
  fun socket(nsp: String): SocketClient {
    var socket = nsps[nsp]

    if (socket == null) {
      socket = SocketClient(this, nsp, this.options)
      nsps[nsp] = socket
    }

    return socket
  }

  /** Called upon a socket close. */
  fun destroy() {
    val nspIds = nsps.keys

    for (nsp in nspIds) {
      val socket = nsps[nsp]

      if (socket?.getActive() == true) {
        Logger.warn("socket $nsp is still active, skipping close")
        return
      }
    }

    close()
  }

  /**
   * Writes a packet.
   *
   * @param packet the [ClientPacket] that needs to be written to the [engine].
   */
  fun packet(packet: ClientPacket) {
    Logger.debug("writing packet $packet")
    engine.write(ClientParser.encode(packet))
  }

  /** Clean up transport subscriptions and packet buffer. */
  fun cleanup() {
    Logger.debug("Manager cleanup")

    for (i in 1 until subs.size) {
      val sub = subs.removeAt(0)
      sub.destroy()
    }
  }

  /** Close the current socket. */
  fun close() {
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
  fun onClose(error: Any?) {
    Logger.debug("onclose:: $error")

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
      val delay = backoff.duration
      Logger.info("will wait %dms before reconnect attempt $delay")

      reconnecting = true
      val timer =
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
                  onReconnect()
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
  fun onReconnect() {
    reconnecting = false
    backoff.reset()
    emit(EVENT_RECONNECT, backoff.attempts)
  }
}

/**
 * Initialize backoff timer with `opts`.
 * - `min` initial timeout in milliseconds [100]
 * - `max` max timeout [10000]
 * - `jitter` [0]
 * - `factor` [2]
 */
class Backoff(
  private val min: Long = 100,
  private var max: Long = 10000,
  private val jitter: Double = 0.0,
  private val factor: Double = 2.0,
) {
  var attempts = 0

  init {
    require(jitter > 0.0 && jitter <= 1.0) {
      throw IllegalArgumentException("jitter should be between 0.0 and 1.0")
    }
  }

  /** Return the backoff duration. */
  val duration: Long
    get() {
      var ms = min(min * factor.pow(attempts++), 1e100)
      if (jitter > 0.0) {
        val rand = Random(0).nextDouble()
        val deviation = floor(rand * jitter * min)
        ms = if (((floor(rand * 10).toInt()).and(1)) == 0) (min - deviation) else (min + deviation)
      }
      // #39: avoid an overflow with negative value
      if (max < +min) {
        ms = max.toDouble()
      }
      return if (min <= 0) max else ms.toLong()
    }

  /** Reset the number of attempts. */
  fun reset() {
    attempts = 0
  }

  fun max(max: Long) {
    this.max = max
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
