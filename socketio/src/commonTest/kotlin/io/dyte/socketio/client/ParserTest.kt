package io.dyte.socketio.client

import io.dyte.socketio.ClientPacket
import io.dyte.socketio.ClientParser
import kotlin.test.Test
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class ParserTest {
  @Test
  fun encodeConnection() {
    val packet = ClientPacket.Connect("/woot")
    TestHelpers.test(packet)
  }

  @Test
  fun encodeDisconnection() {
    val packet = ClientPacket.Disconnect("/woot")
    TestHelpers.test(packet)
  }

  @Test
  fun encodeEvent() {
    val packet1 = ClientPacket.Event("/")
    packet1.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    TestHelpers.test(packet1)

    val packet2 = ClientPacket.Event("/")
    packet2.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    TestHelpers.test(packet2)
  }

  @Test
  fun encodeAck() {
    val packet = ClientPacket.Ack("/")
    packet.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    packet.ackId = 123
    TestHelpers.test(packet)
  }

  @Test
  fun decodeInError() {
    // Random string
    TestHelpers.testDecodeError("asdf")
    // Unknown type
    TestHelpers.testDecodeError(ClientParser.packetTypes.size.toString() + "asdf")
    // Binary event with no `-`
    TestHelpers.testDecodeError(ClientParser.BINARY_EVENT.toString() + "asdf")
    // Binary ack with no `-`
    TestHelpers.testDecodeError(ClientParser.BINARY_ACK.toString() + "asdf")
    // Binary event with no attachment
    TestHelpers.testDecodeError(ClientParser.BINARY_EVENT.toString())
    // event non numeric id
    TestHelpers.testDecodeError(ClientParser.EVENT.toString() + "2sd")
    // event with invalid json data
    TestHelpers.testDecodeError(ClientParser.EVENT.toString() + "2[\"a\",1,{asdf}]")
    TestHelpers.testDecodeError(ClientParser.EVENT.toString() + "2{}")
    // TestHelpers.testDecodeError(ClientParser.EVENT.toString() + "2[]")
    TestHelpers.testDecodeError(ClientParser.EVENT.toString() + "2[null]")
  }
}
