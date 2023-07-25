package engine

import io.dyte.socketio.src.engine.EnginePacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ParserTest {
    val ERROR_DATA = "parser error"

    @Test
    fun encodeAsString() {
        PacketParser.encodePacket(
            EnginePacket(EnginePacket.MESSAGE, "test"),
            false,
            false,
            callback = fun(encodedPacket) {
                assertTrue(encodedPacket is String)
            })
    }

    @Test
    fun decodeAsPacket() {
        PacketParser.encodePacket(
            EnginePacket(EnginePacket.MESSAGE, "test"),
            false,
            callback = fun(data: Any) {
                assertTrue(PacketParser.decodePacket(data) is EnginePacket<*>)
            })
    }

    //
    @Test
    fun noData() {
        PacketParser.encodePacket(
            EnginePacket<Any?>(EnginePacket.MESSAGE),
            false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals( EnginePacket.MESSAGE,p.type)
                assertEquals(null, p.data)
            })
    }

    @Test
    fun encodeOpenPacket() {
        PacketParser.encodePacket(
            EnginePacket(EnginePacket.OPEN, "{\"some\":\"json\"}"),
            supportsBinary = false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals( EnginePacket.OPEN,p.type)
                assertEquals("{\"some\":\"json\"}",p.data)
            })
    }

    @Test
    fun encodeClosePacket() {
        PacketParser.encodePacket(EnginePacket<String>(EnginePacket.CLOSE), false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertTrue(p.type == EnginePacket.CLOSE)
            })
    }

    @Test
    fun encodePingPacket() {
        PacketParser.encodePacket(EnginePacket(EnginePacket.PING, "1"), false, callback =
        fun(data: Any) {
            val p = PacketParser.decodePacket(data)
            assertEquals(EnginePacket.PING, p.type)
            assertEquals("1", p.data)
        })
    }


    @Test
    fun encodePongPacket() {
        PacketParser.encodePacket(EnginePacket(EnginePacket.PONG, "1"), false, callback =
        fun(data: Any) {
            val p = PacketParser.decodePacket(data)
            assertEquals(EnginePacket.PONG, p.type)
            assertEquals("1", p.data)
        })
    }

    @Test
    fun encodeMessagePacket() {
        PacketParser.encodePacket(EnginePacket(EnginePacket.MESSAGE, "aaa"), false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals(EnginePacket.MESSAGE, p.type)
                assertEquals("aaa", p.data)
            })
    }

    @Test
    fun encodeUTF8SpecialCharsMessagePacket() {
        PacketParser.encodePacket(
            EnginePacket<String>(EnginePacket.MESSAGE, "utf8 — string"),
            false, callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals(p.type, EnginePacket.MESSAGE)
                assertEquals(p.data, "utf8 — string")
            })
    }

    @Test
    fun encodeMessagePacketCoercingToString() {
        PacketParser.encodePacket(EnginePacket(EnginePacket.MESSAGE, 1), false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals(EnginePacket.MESSAGE, p.type)
                assertEquals("1", p.data)
            })
    }

    @Test
    fun encodeUpgradePacket() {
        PacketParser.encodePacket(EnginePacket<String>(EnginePacket.UPGRADE), false,
            callback = fun(data: Any) {
                val p = PacketParser.decodePacket(data)
                assertEquals(p.type, EnginePacket.UPGRADE)
            })
    }

    @Test
    fun encodingFormat() {
        PacketParser.encodePacket(EnginePacket(EnginePacket.MESSAGE, "test"), false,
            callback = fun(data: Any) {
                assertTrue((data as String).matches(Regex("[0-9].*")) == true)
            })
        PacketParser.encodePacket(EnginePacket<String>(EnginePacket.MESSAGE), false,
            callback = fun(data: Any) {
                assertTrue((data as String).matches(Regex("[0-9]")) == true)
            })
    }

    @Test
    fun decodeEmptyPayload() {
        val p = PacketParser.decodePacket(null)
        assertEquals(EnginePacket.ERROR, p.type)
        assertEquals(ERROR_DATA, p.data)
    }

    @Test
    fun decodeBadFormat() {
        val p = PacketParser.decodePacket(":::")
        assertEquals(EnginePacket.ERROR, p.type)
        assertEquals(ERROR_DATA, p.data)
    }

    @Test
    fun decodeInexistentTypes() {
        val p = PacketParser.decodePacket("94103")
        assertEquals(EnginePacket.ERROR, p.type)
        assertEquals(ERROR_DATA, p.data)
    }

    @Test
    fun encodePayloads() {
        PacketParser.encodePayload(
            listOf(EnginePacket<Any?>(EnginePacket.PING), EnginePacket<Any?>(EnginePacket.PONG)),
            callback = fun(data: Any) {
                assertTrue(data is String)
            })
    }

    @Test
    fun encodeAndDecodePayloads() {
        PacketParser.encodePayload(
            listOf<EnginePacket<*>>(EnginePacket<String>(EnginePacket.MESSAGE, "a")),
            callback = fun(data: String) {
                val packets = PacketParser.decodePayload(data);
                packets.forEachIndexed { index, packet ->
                    val isLast = index + 1 == packets.size
                    assertTrue(isLast)
                }
            })
        PacketParser.encodePayload(
            listOf<EnginePacket<*>>(
                EnginePacket<String>(EnginePacket.MESSAGE, "a"),
                EnginePacket<Any?>(EnginePacket.PING)
            ), callback = fun(data: String) {
                val packets = PacketParser.decodePayload(data)
                packets.forEachIndexed { index, packet ->
                    val isLast = index + 1 == packets.size
                    if (!isLast) {
                        assertEquals(EnginePacket.MESSAGE, packet.type)
                    } else {
                        assertEquals(EnginePacket.PING, packet.type)
                    }
                }
            })
    }


    @Test
    fun encodeAndDecodeEmptyPayloads() {
        PacketParser.encodePayload(
            listOf<EnginePacket<*>>(),
            callback = fun(data: String) {
                val packets = PacketParser.decodePayload(data)
                packets.forEachIndexed { index, packet ->
                    assertEquals(EnginePacket.OPEN, packet.type)
                    val isLast = index + 1 == packets.size
                    assertTrue(isLast)
                }
            })
    }

    @Test
    fun notUTF8EncodeWhenDealingWithStringsOnly() {
        PacketParser.encodePayload(listOf<EnginePacket<*>>(
            EnginePacket<Any?>(EnginePacket.MESSAGE, "€€€"),
            EnginePacket<Any?>(EnginePacket.MESSAGE, "α")
        ), callback = fun(data: String) {
            assertTrue(data == "4€€€\u001e4α")
        })
    }

    @Test
    fun decodePayloadBadFormat() {
        var packets = PacketParser.decodePayload("")
        packets.forEachIndexed { index, p ->
            val isLast = index + 1 == packets.size
            assertTrue(p.type === EnginePacket.ERROR)
            assertTrue(p.data == ERROR_DATA)
            assertTrue(isLast == true)

        }
        packets = PacketParser.decodePayload("))")
        packets.forEachIndexed { index, p ->
            val isLast = index + 1 == packets.size
            assertTrue(p.type === EnginePacket.ERROR)
            assertTrue(p.data == ERROR_DATA)
            assertTrue(isLast == true)
        }
        packets = PacketParser.decodePayload("99:")
        packets.forEachIndexed { index, p ->
            val isLast = index + 1 == packets.size
            assertTrue(p.type === EnginePacket.ERROR)
            assertTrue(p.data == ERROR_DATA)
            assertTrue(isLast == true)
        }
        packets = PacketParser.decodePayload("aa")
        packets.forEachIndexed { index, p ->
            val isLast = index + 1 == packets.size
            assertEquals(p.type, EnginePacket.ERROR)
            assertEquals(p.data, ERROR_DATA)
            assertTrue(isLast)
        }
    }

    @Test
    fun encodeBinaryMessage() {
        val data = ByteArray(5)
        for (i in data.indices) {
            data[0] = i.toByte()
        }
        PacketParser.encodePacket(
            EnginePacket(EnginePacket.MESSAGE, data),
            false,
            callback = fun(encoded: Any) {
                val p = PacketParser.decodePacket(encoded)
                assertEquals(EnginePacket.MESSAGE, p.type)
                assertTrue(data.contentEquals(p.data as ByteArray?))
            })
    }

    @Test
    fun encodeBinaryContents() {
        val firstBuffer = ByteArray(5)
        for (i in firstBuffer.indices) {
            firstBuffer[0] = i.toByte()
        }
        val secondBuffer = ByteArray(4)
        for (i in secondBuffer.indices) {
            secondBuffer[0] = (firstBuffer.size + i).toByte()
        }
        PacketParser.encodePayload(
            listOf<EnginePacket<*>>(
                EnginePacket(EnginePacket.MESSAGE, firstBuffer),
                EnginePacket(EnginePacket.MESSAGE, secondBuffer)
            ), callback = fun(data: String) {
                val packets = PacketParser.decodePayload(data)
                packets.forEachIndexed { index, p ->
                    val isLast = index + 1 == packets.size
                    assertEquals(p.type, EnginePacket.MESSAGE)
                    if (!isLast) {
                        assertTrue((p.data as ByteArray).contentEquals(firstBuffer))
                    } else {
                        assertTrue((p.data as ByteArray).contentEquals(secondBuffer))
                    }
                }
            })

    }

    @Test
    fun encodeMixedBinaryAndStringContents() {
        val firstBuffer = ByteArray(123)
        for (i in firstBuffer.indices) {
            firstBuffer[0] = i.toByte()
        }
        PacketParser.encodePayload(
            listOf<EnginePacket<*>>(
                EnginePacket(EnginePacket.MESSAGE, firstBuffer),
                EnginePacket(EnginePacket.MESSAGE, "hello"),
                EnginePacket<String>(EnginePacket.CLOSE)
            ), callback = fun(encoded: String) {
                val packets = PacketParser.decodePayload(encoded)
                packets.forEachIndexed { index, p ->
                    if (index == 0) {
                        assertEquals(p.type, EnginePacket.MESSAGE)
                        assertTrue((p.data as ByteArray).contentEquals(firstBuffer))
                    } else if (index == 1) {
                        assertEquals(p.type, EnginePacket.MESSAGE)
                        assertEquals((p.data as String), "hello")
                    } else {
                        assertEquals(p.type, EnginePacket.CLOSE)
                    }
                }
            })
    }
}