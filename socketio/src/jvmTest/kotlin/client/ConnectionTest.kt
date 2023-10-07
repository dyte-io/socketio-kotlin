package client

import ACKFn
import Manager
import ManagerOptions
import SocketClient
import base.Connection
import io.dyte.socketio.IO
import io.dyte.socketio.IOOptions
import io.dyte.socketio.src.engine.asBoolean
import io.dyte.socketio.src.engine.asInt
import io.dyte.socketio.src.engine.asJsonObject
import io.dyte.socketio.src.engine.asString
import java.net.URISyntaxException
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ConnectionTest : Connection("client") {

  fun client(): SocketClient {
    return client(createClientOptions())
  }

  fun client(path: String): SocketClient {
    return client(path, createClientOptions())
  }

  fun client(opts: IOOptions): SocketClient {
    return client(nsp(), opts)
  }

  fun client(path: String, opts: IOOptions): SocketClient {
    return IO.socket(uri() + path, opts)
  }

  fun uri(): String {
    return "http://localhost:" + PORT
  }

  fun nsp(): String {
    return "/"
  }

  fun createClientOptions(): IOOptions {
    val opts = IOOptions()
    opts.forceNew = true
    opts.transports = mutableListOf("polling")
    return opts
  }

  @Test(timeout = TIMEOUT.toLong())
  fun connectToLocalhost() {
    val values: BlockingQueue<Boolean> = LinkedBlockingQueue()
    var socket = client()
    socket.onConnect {
      socket.emit("echo")
      socket.on("echoBack") { values.offer(true) }
    }

    socket.open()
    assertTrue(values.take())
    socket.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  fun startTwoConnectionsWithSamePath() {
    val s1 = client("/")
    val s2 = client("/")
    assertNotEquals(s1.io, s2.io)
    s1.close()
    s2.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun workWithAcks() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val socket = client()
    socket.onConnect {
      socket.emit("callAck")
      socket.onEvent(
        "ack",
        fun(data: Any?, ack: ACKFn) {
          val data = buildJsonObject { put("test", JsonPrimitive(true)) }
          ack?.invoke(listOf(JsonPrimitive(5), data))
        }
      )
      socket.onEvent(
        "ackBack",
        fun(args) {
          try {
            val data = args[1].asJsonObject()
            if (args[0].asInt() == 5 && data?.get("test")?.asBoolean() == true) {
              values.offer("done")
            }
          } catch (e: Exception) {
            throw AssertionError(e)
          }
        }
      )
    }
    socket.connect()
    values.take()
    socket.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun receiveDateWithAck() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val socket = client()
    socket.onConnect {
      try {
        socket.emit(
          "getAckDate",
          Json.decodeFromString<JsonObject>("{\"test\": true}"),
          fun(_args: Any?) {
            val args = _args as ArrayList<JsonElement>
            values.offer(args[0].asString())
          }
        )
      } catch (e: Exception) {}
    }
    socket.connect()
    assertTrue(values.take() is String)
    socket.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun workWithFalse() {
    val values: BlockingQueue<Boolean> = LinkedBlockingQueue()
    val socket = client()
    socket.onConnect {
      socket.emit("echo", false)
      socket.onEvent("echoBack") { data -> values.offer(data[0].asBoolean()) }
    }
    socket.connect()
    assert(values.take() == false)
    socket.close()
  }

  @Test(timeout = 8000.toLong())
  @Throws(InterruptedException::class)
  fun receiveUTF8MultibyteCharacters() {
    val values: BlockingQueue<String> = LinkedBlockingQueue()
    val correct = listOf("てすと", "Я Б Г Д Ж Й", "Ä ä Ü ü ß", "utf8 — string", "utf8 — string")
    val socket = client()
    socket.onConnect {
      socket.onEvent("echoBack") { data ->
        println("Got echoBack: ${data[0].asString()}")
        values.offer(data[0].asString())
      }
      for (data in correct) {
        socket.emit("echo", data)
      }
    }
    socket.connect()
    for (expected in correct) {
      assertEquals(expected, values.take())
    }
    socket.close()
  }

  @Test(timeout = 28000.toLong())
  @Throws(InterruptedException::class)
  fun reconnectByDefault() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val socket = client()
    socket.io.on(
      Manager.EVENT_RECONNECT,
      fun(_) {
        socket.close()
        values.offer("done")
      }
    )
    socket.open()
    Timer()
      .schedule(
        object : TimerTask() {
          override fun run() {
            socket.io.engine.close()
          }
        },
        500
      )
    values.take()
  }

  @Test(timeout = 28000.toLong())
  @Throws(InterruptedException::class)
  fun connectionStateFlow() {
    val values: BlockingQueue<String> = LinkedBlockingQueue()
    val socket = client()

    socket.io.on(
      Manager.EVENT_RECONNECT,
      fun(_) {
        println("1 Reconnected")
        values.offer("reconnected")
      }
    )
    socket.onConnect {
      println("1 connected")
      values.offer("connected")
    }
    socket.onDisconnect { values.offer("disconnected") }
    socket.open()
    Timer()
      .schedule(
        object : TimerTask() {
          override fun run() {
            socket.io.engine.close()
          }
        },
        1000
      )
    assertEquals("connected", values.take())
    assertEquals("disconnected", values.take())
    assertEquals("reconnected", values.take())
    assertEquals("connected", values.take())
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun reconnectManually() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val socket = client()
    socket.once(
      SocketClient.EVENT_CONNECT,
      fun(_) {
        socket.disconnect()
      }
    )
    socket.once(
      SocketClient.EVENT_DISCONNECT,
      fun(_) {
        socket.once(
          SocketClient.EVENT_CONNECT,
          fun(_) {
            socket.disconnect()
            values.offer("done")
          }
        )
        socket.connect()
      }
    )
    socket.connect()
    values.take()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun reconnectAutomaticallyAfterReconnectingManually() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val socket = client()
    socket.once(
      SocketClient.EVENT_CONNECT,
      fun(_) {
        socket.disconnect()
      }
    )
    socket.once(
      SocketClient.EVENT_DISCONNECT,
      fun(_) {
        socket.io.on(
          Manager.EVENT_RECONNECT,
          fun(_) {
            socket.disconnect()
            values.offer("done")
          }
        )
        socket.connect()
        Timer()
          .schedule(
            object : TimerTask() {
              override fun run() {
                socket.io.engine.close()
              }
            },
            500
          )
      }
    )
    socket.connect()
    values.take()
  }

  @Test(timeout = 14000)
  @Throws(InterruptedException::class)
  fun attemptReconnectsAfterAFailedReconnect() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = createClientOptions()
    opts.reconnection = true
    opts.timeout = 0
    opts.reconnectionAttempts = 2
    opts.reconnectionDelay = 10
    val manager = Manager(uri(), opts)
    val socket = manager.socket("/timeout")
    manager.once(
      Manager.EVENT_RECONNECT_FAILED,
      fun(_) {
        val reconnects = intArrayOf(0)
        manager.on(
          Manager.EVENT_RECONNECT_ATTEMPT,
          fun(_) {
            reconnects[0]++
          }
        )
        manager.on(
          Manager.EVENT_RECONNECT_FAILED,
          fun(_) {
            values.offer(reconnects[0])
          }
        )
        socket.connect()
      }
    )
    socket.connect()
    assertEquals(2, values.take() as Int)
    socket.close()
    manager.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun reconnectDelayShouldIncreaseEveryTime() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = createClientOptions()
    opts.reconnection = true
    opts.timeout = 0
    opts.reconnectionAttempts = 3
    opts.reconnectionDelay = 100
    opts.randomizationFactor = 0.2
    opts.autoConnect = false
    val manager = Manager(uri(), opts)
    val socket = manager.socket("/timeout")
    val reconnects = intArrayOf(0)
    val increasingDelay = booleanArrayOf(true)
    val startTime = longArrayOf(0)
    val prevDelay = longArrayOf(0)
    manager.on(
      Manager.EVENT_ERROR,
      fun(_) {
        startTime[0] = Date().time
      }
    )
    manager.on(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        reconnects[0]++
        val currentTime = Date().time
        val delay = currentTime - startTime[0]
        if (delay <= prevDelay[0]) {
          increasingDelay[0] = false
        }
        prevDelay[0] = delay
      }
    )
    manager.on(
      Manager.EVENT_RECONNECT_FAILED,
      fun(_) {
        values.offer(true)
      }
    )
    socket.connect()
    values.take()
    assertEquals(3, reconnects[0])
    assertEquals(true, increasingDelay[0])
    socket.close()
    manager.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(URISyntaxException::class, InterruptedException::class)
  fun notReconnectWhenForceClosed() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = createClientOptions()
    opts.timeout = 0
    opts.reconnectionDelay = 10
    val socket = IO.socket(uri() + "/invalid", opts)
    socket.io.on(
      Manager.EVENT_ERROR,
      fun(_) {
        socket.io.on(
          Manager.EVENT_RECONNECT_ATTEMPT,
          fun(_) {
            values.offer(false)
          }
        )
        socket.disconnect()
        Timer()
          .schedule(
            object : TimerTask() {
              override fun run() {
                values.offer(true)
              }
            },
            500
          )
      }
    )
    socket.connect()
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(URISyntaxException::class, InterruptedException::class)
  fun stopReconnectingWhenForceClosed() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = createClientOptions()
    opts.timeout = 0
    opts.reconnectionDelay = 10
    val socket = IO.socket(uri() + "/invalid", opts)
    socket.io.once(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        socket.io.on(
          Manager.EVENT_RECONNECT_ATTEMPT,
          fun(_) {
            values.offer(false)
          }
        )
        socket.disconnect()
        // set a timer to let reconnection possibly fire
        Timer()
          .schedule(
            object : TimerTask() {
              override fun run() {
                values.offer(true)
              }
            },
            500
          )
      }
    )
    socket.connect()
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun reconnectAfterStoppingReconnection() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = createClientOptions()
    opts.forceNew = true
    opts.timeout = 0
    opts.reconnectionDelay = 10
    val socket = client("/invalid", opts)
    socket.io.once(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        socket.io.once(
          Manager.EVENT_RECONNECT_ATTEMPT,
          fun(_) {
            values.offer("done")
          }
        )
        socket.disconnect()
        socket.connect()
      }
    )
    socket.connect()
    values.take()
    socket.disconnect()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun stopReconnectingOnASocketAndKeepToReconnectOnAnother() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val manager = Manager(uri(), createClientOptions())
    val socket1 = manager.socket("/")
    val socket2 = manager.socket("/asd")
    manager.on(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        socket1.on(
          SocketClient.EVENT_CONNECT,
          fun(_) {
            values.offer(false)
          }
        )
        socket2.on(
          SocketClient.EVENT_CONNECT,
          fun(_) {
            Timer()
              .schedule(
                object : TimerTask() {
                  override fun run() {
                    socket2.disconnect()
                    manager.close()
                    values.offer(true)
                  }
                },
                500
              )
          }
        )
        socket1.disconnect()
      }
    )
    socket1.connect()
    socket2.connect()
    Timer()
      .schedule(
        object : TimerTask() {
          override fun run() {
            manager.engine.close()
          }
        },
        1000
      )
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun connectWhileDisconnectingAnotherSocket() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val manager = Manager(uri(), createClientOptions())
    val socket1 = manager.socket("/foo")
    socket1.on(
      SocketClient.EVENT_CONNECT,
      fun(_) {
        val socket2 = manager.socket("/asd")
        socket2.on(
          SocketClient.EVENT_CONNECT,
          fun(_) {
            values.offer("done")
            socket2.disconnect()
          }
        )
        socket2.open()
        socket1.disconnect()
      }
    )
    socket1.open()
    values.take()
    manager.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun tryToReconnectTwiceAndFailWithIncorrectAddress() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = ManagerOptions()
    opts.reconnection = true
    opts.reconnectionAttempts = 2
    opts.reconnectionDelay = 10
    val manager = Manager("http://localhost:3940", opts)
    val socket = manager.socket("/asd")
    val reconnects = intArrayOf(0)

    manager.on(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        reconnects[0]++
      }
    )
    manager.on(
      Manager.EVENT_RECONNECT_FAILED,
      fun(_) {
        values.offer(reconnects[0])
      }
    )
    socket.open()
    assertEquals(2, values.take() as Int)
    socket.close()
    manager.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun tryToReconnectTwiceAndFailWithImmediateTimeout() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = ManagerOptions()
    opts.reconnection = true
    opts.timeout = 0
    opts.reconnectionAttempts = 2
    opts.reconnectionDelay = 10
    val manager = Manager(uri(), opts)
    val reconnects = intArrayOf(0)
    val socket = manager.socket("/timeout")
    manager.on(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        reconnects[0]++
      }
    )
    manager.on(
      Manager.EVENT_RECONNECT_FAILED,
      fun(_) {
        socket.close()
        manager.close()
        values.offer(reconnects[0])
      }
    )

    socket.open()
    assertEquals(2, values.take() as Int)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun notTryToReconnectWithIncorrectPortWhenReconnectionDisabled() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val opts = ManagerOptions()
    opts.reconnection = false
    val manager = Manager("http://localhost:9823/", opts)
    val socket = manager.socket("/invalid")
    manager.on(
      Manager.EVENT_RECONNECT_ATTEMPT,
      fun(_) {
        socket.close()
        throw RuntimeException()
      }
    )
    manager.on(
      Manager.EVENT_ERROR,
      fun(_) {
        val timer = Timer()
        timer.schedule(
          object : TimerTask() {
            override fun run() {
              socket.close()
              manager.close()
              values.offer("done")
            }
          },
          1000
        )
      }
    )
    socket.open()
    values.take()
  }
}
