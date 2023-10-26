package io.dyte.socketio.engine

import io.dyte.socketio.base.Connection
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTest : Connection("engine") {

  @Test(timeout = TIMEOUT.toLong())
  fun connectToLocalhost() {
    val values: BlockingQueue<Boolean> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        values.offer(true)
        socket.close()
      }
    )

    socket.open()
    assertTrue(values.take())
  }

  @Test(timeout = TIMEOUT.toLong())
  fun receiveMultibyteUTF8StringsWithPolling() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        socket.send("cash money €€€")
        socket.on(
          EngineSocket.EVENT_MESSAGE,
          handler = { data: Any? ->
            if (data as String == "hi") return
            values.offer(data)
            socket.close()
          }
        )
      }
    )
    socket.open()
    assertEquals("cash money €€€", values.take() as String)
  }

  @Test(timeout = TIMEOUT.toLong())
  fun receiveEmoji() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        socket.send("\uD800\uDC00-\uDB7F\uDFFF\uDB80\uDC00-\uDBFF\uDFFF\uE000-\uF8FF")
        socket.on(
          EngineSocket.EVENT_MESSAGE,
          handler = { data: Any? ->
            if (data as String == "hi") return
            values.offer(data)
            socket.close()
          }
        )
      }
    )
    socket.open()
    assertEquals(
      "\uD800\uDC00-\uDB7F\uDFFF\uDB80\uDC00-\uDBFF\uDFFF\uE000-\uF8FF",
      values.take() as String,
    )
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun notSendPacketsIfSocketCloses() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        val noPacket = booleanArrayOf(true)
        socket.on(EngineSocket.EVENT_PACKET_CREATE, handler = { _ -> noPacket[0] = false })
        socket.close()
        socket.send("hi")
        val timer = Timer()
        timer.schedule(
          object : TimerTask() {
            override fun run() {
              values.offer(noPacket[0])
            }
          },
          1200
        )
      }
    )
    socket.open()
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun deferCloseWhenUpgrading() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        val upgraded = booleanArrayOf(false)
        socket.on(EngineSocket.EVENT_UPGRADE, handler = { _ -> upgraded[0] = true })
        socket.on(
          EngineSocket.EVENT_UPGRADING,
          handler = { _ ->
            socket.on(EngineSocket.EVENT_CLOSE, handler = { _ -> values.offer(upgraded[0]) })
            socket.close()
          }
        )
      }
    )
    socket.open()
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun closeOnUpgradeErrorIfClosingIsDeferred() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        val upgradError = booleanArrayOf(false)
        socket.on(EngineSocket.EVENT_UPGRADE_ERROR, handler = { _ -> upgradError[0] = true })
        socket.on(
          EngineSocket.EVENT_UPGRADING,
          handler = { _ ->
            socket.on(EngineSocket.EVENT_CLOSE, handler = { _ -> values.offer(upgradError[0]) })
            socket.transport?.onError("upgrade error", "")
            socket.close()
          }
        )
      }
    )
    socket.open()
    assertTrue(values.take() as Boolean)
  }

  @Throws(InterruptedException::class)
  fun notSendPacketsIfClosingIsDeferred() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        val noPacket = booleanArrayOf(true)
        socket.on(
          EngineSocket.EVENT_UPGRADING,
          handler = { _ ->
            socket.on(EngineSocket.EVENT_PACKET_CREATE, handler = { _ -> noPacket[0] = false })
            socket.close()
            socket.send("hi")
          }
        )
        Timer()
          .schedule(
            object : TimerTask() {
              override fun run() {
                values.offer(noPacket[0])
              }
            },
            1200
          )
      }
    )
    socket.open()
    assertTrue(values.take() as Boolean)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun sendAllBufferedPacketsIfClosingIsDeferred() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_OPEN,
      handler = { _ ->
        socket.on(
          EngineSocket.EVENT_UPGRADING,
          handler = { _ ->
            socket.send("hi")
            socket.close()
          }
        )
        socket.on(
          EngineSocket.EVENT_CLOSE,
          handler = { _ -> values.offer(socket.writeBuffer.size) }
        )
      }
    )
    socket.open()
    assertEquals(0, values.take() as Int)
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun receivePing() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    var socket = EngineSocket(_opts = createOptions())
    socket.on(
      EngineSocket.EVENT_PING,
      handler = { _ ->
        values.offer("end")
        socket.close()
      }
    )
    socket.open()
    assertEquals("end", values.take())
  }
}
