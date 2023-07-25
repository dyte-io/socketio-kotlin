package engine

import EngineSocket
import EngingeSocketOptions
import kotlin.test.Test
import kotlin.test.assertEquals


internal class SocketTest {

    @Test
    fun filterUpgrades() {
        val opts = EngingeSocketOptions()
        opts.transports = mutableListOf("polling")
        val socket = EngineSocket(null, opts)
        val upgrades = listOf("polling", "websocket")
        val expected = listOf("polling")
        assertEquals(socket.filterUpgrades(upgrades), expected)
    }

    @Test
    fun properlyParseHttpUriWithoutPort() {
        val client = EngineSocket("http://localhost")
        assertEquals("localhost", client.hostname)
        assertEquals(80, client.port)
    }

    @Test
    fun properlyParseHttpsUriWithoutPort() {
        val client = EngineSocket("https://localhost")
        assertEquals("localhost", client.hostname)
        assertEquals(443, client.port)
    }

    @Test
    fun properlyParseWssUriWithoutPort() {
        val client = EngineSocket("wss://localhost")
        assertEquals("localhost", client.hostname)
        assertEquals(443, client.port)
    }

    @Test
    fun properlyParseWssUriWithPort() {
        val client = EngineSocket("wss://localhost:2020")
        assertEquals("localhost", client.hostname)
        assertEquals(2020, client.port)
    }

    @Test
    fun properlyParseHostWithPort() {
        val opts = EngingeSocketOptions()
        opts.host = "localhost"
        opts.port = 8080
        val client = EngineSocket(_opts = opts)
        assertEquals("localhost", client.hostname)
        assertEquals(8080, client.port)
    }

    @Test
    fun properlyParseIPv6UriWithoutPort() {
        val client = EngineSocket("http://[::1]")
        assertEquals("::1", client.hostname)
        assertEquals(80, client.port)
    }

    @Test
    fun properlyParseIPv6UriWithPort() {
        val client = EngineSocket("http://[::1]:8080")
        assertEquals("::1", client.hostname)
        assertEquals(8080, client.port)
    }

    @Test
    fun properlyParseIPv6HostWithoutPort1() {
        val opts = EngingeSocketOptions()
        opts.host = "[::1]"
        val client = EngineSocket(_opts=opts)
        assertEquals("::1", client.hostname)
        assertEquals(80, client.port)
    }

    @Test
    fun properlyParseIPv6HostWithoutPort2() {
        val opts = EngingeSocketOptions()
        opts.secure = true
        opts.host = "[::1]"
        val client = EngineSocket(_opts=opts)
        assertEquals("::1", client.hostname)
        assertEquals(443, client.port)
    }

    @Test
    fun properlyParseIPv6HostWithPort() {
        val opts = EngingeSocketOptions()
        opts.host = "[::1]"
        opts.port = 8080
        val client = EngineSocket(_opts=opts)
        assertEquals("::1", client.hostname)
        assertEquals(8080, client.port)
    }

    @Test
    fun properlyParseIPv6HostWithoutBrace() {
        val opts = EngingeSocketOptions()
        opts.host = "::1"
        val client = EngineSocket(_opts=opts)
        assertEquals("::1", client.hostname)
        assertEquals(80, client.port)
    }
}