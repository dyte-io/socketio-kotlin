package engine

import EngineSocket
import base.Connection
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals


class BinaryWSTest : Connection("engine") {

    @Test(timeout = 100L)
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
        socket.on(EngineSocket.EVENT_OPEN, fun(_) {
            socket.send(binaryData)
            socket.on(EngineSocket.EVENT_MESSAGE, fun(data: Any?) {
                if ("hi" == data) return
                values.offer(data)
            })
        })
        socket.open()
        assert(binaryData.contentEquals(values.take() as ByteArray))
        socket.close()
    }

    @Test(timeout = 100L)
    @Throws(InterruptedException::class)
    fun receiveBinaryDataAndMultibyteUTF8String() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        val binaryData = ByteArray(5)
        for (i in binaryData.indices) {
            binaryData[i] = i.toByte()
        }
        val msg = intArrayOf(0)
        val opts = createOptions()
        opts.port = PORT
        opts.transports = mutableListOf<String>("websocket")
        val socket = EngineSocket(_opts = opts)
        socket.on(EngineSocket.EVENT_OPEN, fun(_) {
            socket.send(binaryData)
            socket.send("cash money €€€")
            socket.on(EngineSocket.EVENT_MESSAGE, fun(data: Any?) {
                if ("hi" == data) return
                values.offer(data)
                msg[0]++
            })
        })
        socket.open()
        assert(binaryData.contentEquals(values.take() as ByteArray))
        assertEquals("cash money €€€", values.take() as String)
        socket.close()
    }
}