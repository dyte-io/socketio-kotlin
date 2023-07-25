package engine

import PollingTransport
import Transport
import TransportOptions
import WebSocketTransport
import XHRTransport
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class TransportTest {
    @Test
    fun uri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.secure = false
        opt.query = Parameters.build {
            append("sid","test")
        }
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("http://localhost/engine.io?sid=test"))
    }

    @Test
    fun uriWithDefaultPort() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.secure = false
        opt.query = Parameters.build {
            append("sid","test")
        }
        opt.port = 80
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("http://localhost/engine.io?sid=test"))
    }

    @Test
    fun uriWithPort() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.secure = false
        opt.query = Parameters.build {
            append("sid","test")
        }
        opt.port = 3000
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("http://localhost:3000/engine.io?sid=test"))
    }

    @Test
    fun httpsUriWithDefaultPort() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.secure = true
        opt.query = Parameters.build {
            append("sid","test")
        }
        opt.port = 443
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("https://localhost/engine.io?sid=test"))
    }

    @Test
    fun timestampedUri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.timestampParam = "t"
        opt.timestampRequests = true
        val polling = XHRTransport(opt)
        assertTrue(
            polling.uri().matches(Regex("http://localhost/engine.io\\?(j=[0-9]+&)?t=[0-9A-Za-z-_.]+"))
        )
    }

    @Test
    fun ipv6Uri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "::1"
        opt.secure = false
        opt.port = 80
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("http://[::1]/engine.io"))
    }

    @Test
    fun ipv6UriWithPort() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "::1"
        opt.secure = false
        opt.port = 8080
        opt.timestampRequests = false
        val polling = XHRTransport(opt)
        assert(polling.uri().contains("http://[::1]:8080/engine.io"))
    }

    @Test
    fun wsUri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "test"
        opt.secure = false
        opt.query = Parameters.build {
            append("transport", "websocket")
        }
        opt.timestampRequests = false
        val ws = WebSocketTransport(opt)
        assertEquals("ws://test/engine.io?transport=websocket", ws.uri())
    }

    @Test
    fun wssUri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "test"
        opt.secure = true
        opt.timestampRequests = false
        val ws = WebSocketTransport(opt)
        assertEquals("wss://test/engine.io", ws.uri())
    }

    @Test
    fun wsTimestampedUri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "localhost"
        opt.timestampParam = "woot"
        opt.timestampRequests = true
        val ws = WebSocketTransport(opt)
        assertTrue(ws.uri().matches(Regex("ws://localhost/engine.io\\?woot=[0-9A-Za-z-_.]+")))
    }

    @Test
    fun wsIPv6Uri() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "::1"
        opt.secure = false
        opt.port = 80
        opt.timestampRequests = false
        val ws = WebSocketTransport(opt)
        assert(ws.uri().contains("ws://[::1]/engine.io"))
    }

    @Test
    fun wsIPv6UriWithPort() {
        val opt = TransportOptions()
        opt.path = "/engine.io"
        opt.hostname = "::1"
        opt.secure = false
        opt.port = 8080
        opt.timestampRequests = false
        val ws = WebSocketTransport(opt)
        assert(ws.uri().contains("ws://[::1]:8080/engine.io"))
    }

}
