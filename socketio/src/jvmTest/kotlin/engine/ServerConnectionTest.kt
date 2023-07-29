package engine

import EngineSocket
import SocketClient
import Transport
import base.Connection
import io.dyte.socketio.src.engine.EnginePacket
import io.dyte.socketio.src.engine.types.HandshakeModel
import java.net.URISyntaxException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class ServerConnectionTest : Connection("engine") {
    private var socket: SocketClient? = null

    @Test(timeout = TIMEOUT.toLong())
    @Throws(
        URISyntaxException::class,
        InterruptedException::class
    )
    fun openAndClose() {
        val events: BlockingQueue<String> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_OPEN, handler = fun(_) {
            events.offer("onopen")
        })
        socket.on(EngineSocket.EVENT_CLOSE, handler = fun(_) {
            events.offer("onclose")
        })
        socket.open()
        assertEquals("onopen", events.take())
        socket.close()
        assertEquals("onclose", events.take())
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(
        URISyntaxException::class,
        InterruptedException::class
    )
    fun messages() {
        val events: BlockingQueue<String> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_OPEN, handler = fun(_) {
            socket.send("hello")
        })
        socket.on(EngineSocket.EVENT_MESSAGE, handler = fun(data: Any?) {
            events.offer(data as String)
        })
        socket.open()
        assertEquals("hi", events.take())
        assertEquals("hello", events.take())
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(
        URISyntaxException::class,
        InterruptedException::class
    )
    fun handshake() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_HANDSHAKE, handler = fun(data: Any?) {
            values.offer(data)
        })
        socket.open()
        val data = values.take() as EnginePacket.Open
        assert(data.sid != null)
        assert(data.upgrades.isNotEmpty())
        assert(data.pingTimeout > 0L)
        assert(data.pingInterval > 0L)
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(
        URISyntaxException::class,
        InterruptedException::class
    )
    fun upgrade() {
        val events: BlockingQueue<Any> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_UPGRADING, handler = fun(data: Any?) {
            events.offer(data)
        })
        socket.on(EngineSocket.EVENT_UPGRADE, handler = fun(data: Any?) {
            events.offer(data)
        })
        socket.open()
        val args1 = events.take()
        assert(args1 is Transport)
        val transport1 = args1 as Transport
        assertNotNull(transport1)
        val args2 = events.take()
        assert(args2 is Transport)
        val transport2 = args2 as Transport
        assertNotNull(transport2)
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(
        URISyntaxException::class,
        InterruptedException::class
    )
    fun pollingHeaders() {
        val messages: BlockingQueue<String> = LinkedBlockingQueue()
        val opts = createOptions();
        opts.transports = mutableListOf("polling")
        opts.extraHeaders = mutableMapOf("X-EngineIO" to "foo")
        var socket = EngineSocket(_opts = opts)
        socket.on(EngineSocket.EVENT_TRANSPORT, handler = fun(data: Any?) {
            val transport = data as Transport
            transport.on(Transport.EVENT_RESPONSE_HEADERS, handler = fun(data: Any?) {
                val headers = data as Map<String, List<String>>
                val values = headers["X-EngineIO"]!!
                messages.offer(values[0])
                messages.offer(values[1])
            })
        })
        socket.open()
        assertEquals("hi", messages.take())
        assertEquals("foo", messages.take())
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(InterruptedException::class)
    fun rememberWebsocket() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_UPGRADE, fun(data: Any?) {
            val transport = data as Transport
            socket.close()
            if ("websocket".equals(transport.name)) {
                val _opts = createOptions()
                _opts.rememberUpgrade = true
                val socket2 = EngineSocket(_opts = _opts)
                socket2.open()
                values.offer(socket2.transport?.name)
                socket2.close()
            }
        })
        socket.open()
        values.offer(socket.transport?.name)

        assertEquals("polling", values.take() as String)
        assertEquals("websocket", values.take() as String)
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(InterruptedException::class)
    fun notrememberWebsocket() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        var socket = EngineSocket(_opts = createOptions())
        socket.on(EngineSocket.EVENT_UPGRADE, fun(data: Any?) {
            val transport = data as Transport
            socket.close()
            if ("websocket".equals(transport.name)) {
                val _opts = createOptions()
                _opts.rememberUpgrade = false
                val socket2 = EngineSocket(_opts = _opts)
                socket2.open()
                values.offer(socket2.transport?.name)
                socket2.close()
            }
        })
        socket.open()
        values.offer(socket.transport?.name)

        assertEquals("polling", values.take() as String)
        assertEquals("polling", values.take() as String)
    }

}