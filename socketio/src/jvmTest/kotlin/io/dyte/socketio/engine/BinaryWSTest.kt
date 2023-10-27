package io.dyte.socketio.engine

import io.dyte.socketio.base.Connection
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals

class BinaryWSTest : Connection("engine") {

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun receiveBinaryData() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val binaryData = ByteArray(5)
    for (i in binaryData.indices) {
      binaryData[i] = i.toByte()
    }
    val opts = createOptions()
    opts.port = PORT
    opts.transports = mutableListOf<String>("websocket")
    val socket = EngineSocket(_opts = opts)
    socket.on(EngineSocket.EVENT_OPEN) { _ ->
      socket.send(binaryData)
      socket.on(EngineSocket.EVENT_MESSAGE) { data: Any? ->
        if ("hi" == data) return@on
        values.offer(data)
      }
    }
    socket.open()
    assert(binaryData.contentEquals(values.take() as ByteArray))
    socket.close()
  }

  @Test(timeout = TIMEOUT.toLong())
  @Throws(InterruptedException::class)
  fun receiveBinaryDataAndMultibyteUTF8String() {
    val values: BlockingQueue<Any> = LinkedBlockingQueue()
    val binaryData = ByteArray(5)
    for (i in binaryData.indices) {
      binaryData[i] = i.toByte()
    }
    val opts = createOptions()
    opts.port = PORT
    opts.transports = mutableListOf<String>("websocket")
    val socket = EngineSocket(_opts = opts)
    socket.on(EngineSocket.EVENT_OPEN) { _ ->
      socket.send(binaryData)
      socket.send("cash money €€€")
      socket.on(EngineSocket.EVENT_MESSAGE) { data: Any? ->
        if ("hi" == data) return@on
        values.offer(data)
      }
    }
    socket.open()
    assert(binaryData.contentEquals(values.take() as ByteArray))
    assertEquals("cash money €€€", values.take() as String)
    socket.close()
  }
}
