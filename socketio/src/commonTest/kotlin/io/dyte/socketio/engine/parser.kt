package io.dyte.socketio.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTest {
  val ERROR_DATA = "parser error"

  @Test
  fun encodeAsString() {
    val encodedPacket = EnginePacketParser.encodePacket(EnginePacket.Message("test"))
    assertTrue(encodedPacket is String)
  }

  @Test
  fun decodeAsPacket() {
    val data = EnginePacketParser.encodePacket(EnginePacket.Message("test"))
    assertTrue(EnginePacketParser.decodePacket(data) is EnginePacket)
  }

  //
  @Test
  fun noData() {
    val data = EnginePacketParser.encodePacket(EnginePacket.Message())
    val decoded = EnginePacketParser.decodePacket(data)
    assertTrue(decoded is EnginePacket.Message)
    assertEquals(null, decoded.payload)
  }

  //    @Test
  //    fun encodeOpenPacket() {
  //        EnginePacketParser.serializePacket(
  //            EnginePacket(EnginePacket.OPEN, "{\"some\":\"json\"}"),
  //            supportsBinary = false,
  //            callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertEquals(EnginePacket.OPEN, p.type)
  //                assertEquals("{\"some\":\"json\"}", p.payload)
  //            })
  //    }
  //
  //    @Test
  //    fun encodeClosePacket() {
  //        EnginePacketParser.serializePacket(EnginePacket<String>(EnginePacket.CLOSE), false,
  //            callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertTrue(p.type == EnginePacket.CLOSE)
  //            })
  //    }
  //
  //    @Test
  //    fun encodePingPacket() {
  //        EnginePacketParser.serializePacket(EnginePacket(EnginePacket.PING, "1"), false, callback
  // =
  //        fun(data: Any) {
  //            val p = EnginePacketParser.deserializePacket(data)
  //            assertEquals(EnginePacket.PING, p.type)
  //            assertEquals("1", p.payload)
  //        })
  //    }
  //
  //
  //    @Test
  //    fun encodePongPacket() {
  //        EnginePacketParser.serializePacket(EnginePacket(EnginePacket.PONG, "1"), false, callback
  // =
  //        fun(data: Any) {
  //            val p = EnginePacketParser.deserializePacket(data)
  //            assertEquals(EnginePacket.PONG, p.type)
  //            assertEquals("1", p.payload)
  //        })
  //    }
  //
  //    @Test
  //    fun encodeMessagePacket() {
  //        EnginePacketParser.serializePacket(EnginePacket(EnginePacket.MESSAGE, "aaa"), false,
  //            callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertEquals(EnginePacket.MESSAGE, p.type)
  //                assertEquals("aaa", p.payload)
  //            })
  //    }
  //
  //    @Test
  //    fun encodeUTF8SpecialCharsMessagePacket() {
  //        EnginePacketParser.serializePacket(
  //            EnginePacket<String>(EnginePacket.MESSAGE, "utf8 — string"),
  //            false, callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertEquals(p.type, EnginePacket.MESSAGE)
  //                assertEquals(p.payload, "utf8 — string")
  //            })
  //    }
  //
  //    @Test
  //    fun encodeMessagePacketCoercingToString() {
  //        EnginePacketParser.serializePacket(EnginePacket(EnginePacket.MESSAGE, 1), false,
  //            callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertEquals(EnginePacket.MESSAGE, p.type)
  //                assertEquals("1", p.payload)
  //            })
  //    }
  //
  //    @Test
  //    fun encodeUpgradePacket() {
  //        EnginePacketParser.serializePacket(EnginePacket<String>(EnginePacket.UPGRADE), false,
  //            callback = fun(data: Any) {
  //                val p = EnginePacketParser.deserializePacket(data)
  //                assertEquals(p.type, EnginePacket.UPGRADE)
  //            })
  //    }
  //
  //    @Test
  //    fun encodingFormat() {
  //        EnginePacketParser.serializePacket(EnginePacket(EnginePacket.MESSAGE, "test"), false,
  //            callback = fun(data: Any) {
  //                assertTrue((data as String).matches(Regex("[0-9].*")) == true)
  //            })
  //        EnginePacketParser.serializePacket(EnginePacket<String>(EnginePacket.MESSAGE), false,
  //            callback = fun(data: Any) {
  //                assertTrue((data as String).matches(Regex("[0-9]")) == true)
  //            })
  //    }
  //
  //    @Test
  //    fun decodeEmptyPayload() {
  //        val p = EnginePacketParser.deserializePacket(null)
  //        assertEquals(EnginePacket.ERROR, p.type)
  //        assertEquals(ERROR_DATA, p.payload)
  //    }
  //
  //    @Test
  //    fun decodeBadFormat() {
  //        val p = EnginePacketParser.deserializePacket(":::")
  //        assertEquals(EnginePacket.ERROR, p.type)
  //        assertEquals(ERROR_DATA, p.payload)
  //    }
  //
  //    @Test
  //    fun decodeInexistentTypes() {
  //        val p = EnginePacketParser.deserializePacket("94103")
  //        assertEquals(EnginePacket.ERROR, p.type)
  //        assertEquals(ERROR_DATA, p.payload)
  //    }
  //
  //    @Test
  //    fun encodePayloads() {
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf(EnginePacket<Any?>(EnginePacket.PING),
  // EnginePacket<Any?>(EnginePacket.PONG)),
  //            callback = fun(data: Any) {
  //                assertTrue(data is String)
  //            })
  //    }
  //
  //    @Test
  //    fun encodeAndDecodePayloads() {
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf<EnginePacket<*>>(EnginePacket<String>(EnginePacket.MESSAGE, "a")),
  //            callback = fun(data: String) {
  //                val packets = EnginePacketParser.deserializeMultiplePacket(data);
  //                packets.forEachIndexed { index, packet ->
  //                    val isLast = index + 1 == packets.size
  //                    assertTrue(isLast)
  //                }
  //            })
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf<EnginePacket<*>>(
  //                EnginePacket<String>(EnginePacket.MESSAGE, "a"),
  //                EnginePacket<Any?>(EnginePacket.PING)
  //            ), callback = fun(data: String) {
  //                val packets = EnginePacketParser.deserializeMultiplePacket(data)
  //                packets.forEachIndexed { index, packet ->
  //                    val isLast = index + 1 == packets.size
  //                    if (!isLast) {
  //                        assertEquals(EnginePacket.MESSAGE, packet.type)
  //                    } else {
  //                        assertEquals(EnginePacket.PING, packet.type)
  //                    }
  //                }
  //            })
  //    }
  //
  //
  //    @Test
  //    fun encodeAndDecodeEmptyPayloads() {
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf<EnginePacket<*>>(),
  //            callback = fun(data: String) {
  //                val packets = EnginePacketParser.deserializeMultiplePacket(data)
  //                packets.forEachIndexed { index, packet ->
  //                    assertEquals(EnginePacket.OPEN, packet.type)
  //                    val isLast = index + 1 == packets.size
  //                    assertTrue(isLast)
  //                }
  //            })
  //    }
  //
  //    @Test
  //    fun notUTF8EncodeWhenDealingWithStringsOnly() {
  //        EnginePacketParser.serializeMultiplePacket(listOf<EnginePacket<*>>(
  //            EnginePacket<Any?>(EnginePacket.MESSAGE, "€€€"),
  //            EnginePacket<Any?>(EnginePacket.MESSAGE, "α")
  //        ), callback = fun(data: String) {
  //            assertTrue(data == "4€€€\u001e4α")
  //        })
  //    }
  //
  //    @Test
  //    fun decodePayloadBadFormat() {
  //        var packets = EnginePacketParser.deserializeMultiplePacket("")
  //        packets.forEachIndexed { index, p ->
  //            val isLast = index + 1 == packets.size
  //            assertTrue(p.type === EnginePacket.ERROR)
  //            assertTrue(p.payload == ERROR_DATA)
  //            assertTrue(isLast == true)
  //
  //        }
  //        packets = EnginePacketParser.deserializeMultiplePacket("))")
  //        packets.forEachIndexed { index, p ->
  //            val isLast = index + 1 == packets.size
  //            assertTrue(p.type === EnginePacket.ERROR)
  //            assertTrue(p.payload == ERROR_DATA)
  //            assertTrue(isLast == true)
  //        }
  //        packets = EnginePacketParser.deserializeMultiplePacket("99:")
  //        packets.forEachIndexed { index, p ->
  //            val isLast = index + 1 == packets.size
  //            assertTrue(p.type === EnginePacket.ERROR)
  //            assertTrue(p.payload == ERROR_DATA)
  //            assertTrue(isLast == true)
  //        }
  //        packets = EnginePacketParser.deserializeMultiplePacket("aa")
  //        packets.forEachIndexed { index, p ->
  //            val isLast = index + 1 == packets.size
  //            assertEquals(p.type, EnginePacket.ERROR)
  //            assertEquals(p.payload, ERROR_DATA)
  //            assertTrue(isLast)
  //        }
  //    }
  //
  //    @Test
  //    fun encodeBinaryMessage() {
  //        val data = ByteArray(5)
  //        for (i in data.indices) {
  //            data[0] = i.toByte()
  //        }
  //        EnginePacketParser.serializePacket(
  //            EnginePacket(EnginePacket.MESSAGE, data),
  //            false,
  //            callback = fun(encoded: Any) {
  //                val p = EnginePacketParser.deserializePacket(encoded)
  //                assertEquals(EnginePacket.MESSAGE, p.type)
  //                assertTrue(data.contentEquals(p.payload as ByteArray?))
  //            })
  //    }
  //
  //    @Test
  //    fun encodeBinaryContents() {
  //        val firstBuffer = ByteArray(5)
  //        for (i in firstBuffer.indices) {
  //            firstBuffer[0] = i.toByte()
  //        }
  //        val secondBuffer = ByteArray(4)
  //        for (i in secondBuffer.indices) {
  //            secondBuffer[0] = (firstBuffer.size + i).toByte()
  //        }
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf<EnginePacket<*>>(
  //                EnginePacket(EnginePacket.MESSAGE, firstBuffer),
  //                EnginePacket(EnginePacket.MESSAGE, secondBuffer)
  //            ), callback = fun(data: String) {
  //                val packets = EnginePacketParser.deserializeMultiplePacket(data)
  //                packets.forEachIndexed { index, p ->
  //                    val isLast = index + 1 == packets.size
  //                    assertEquals(p.type, EnginePacket.MESSAGE)
  //                    if (!isLast) {
  //                        assertTrue((p.payload as ByteArray).contentEquals(firstBuffer))
  //                    } else {
  //                        assertTrue((p.payload as ByteArray).contentEquals(secondBuffer))
  //                    }
  //                }
  //            })
  //
  //    }
  //
  //    @Test
  //    fun encodeMixedBinaryAndStringContents() {
  //        val firstBuffer = ByteArray(123)
  //        for (i in firstBuffer.indices) {
  //            firstBuffer[0] = i.toByte()
  //        }
  //        EnginePacketParser.serializeMultiplePacket(
  //            listOf<EnginePacket<*>>(
  //                EnginePacket(EnginePacket.MESSAGE, firstBuffer),
  //                EnginePacket(EnginePacket.MESSAGE, "hello"),
  //                EnginePacket<String>(EnginePacket.CLOSE)
  //            ), callback = fun(encoded: String) {
  //                val packets = EnginePacketParser.deserializeMultiplePacket(encoded)
  //                packets.forEachIndexed { index, p ->
  //                    if (index == 0) {
  //                        assertEquals(p.type, EnginePacket.MESSAGE)
  //                        assertTrue((p.payload as ByteArray).contentEquals(firstBuffer))
  //                    } else if (index == 1) {
  //                        assertEquals(p.type, EnginePacket.MESSAGE)
  //                        assertEquals((p.payload as String), "hello")
  //                    } else {
  //                        assertEquals(p.type, EnginePacket.CLOSE)
  //                    }
  //                }
  //            })
  //    }
}
