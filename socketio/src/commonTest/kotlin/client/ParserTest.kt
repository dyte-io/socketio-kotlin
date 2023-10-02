package client

import io.dyte.socketio.src.ClientPacket
import kotlin.test.Test
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class ParserTest {
  @Test
  fun encodeConnection() {
    val packet = ClientPacket.Connect("/woot")
    helpers.test(packet)
  }

  @Test
  fun encodeDisconnection() {
    val packet = ClientPacket.Disconnect("/woot")
    helpers.test(packet)
  }

  @Test
  fun encodeEvent() {
    val packet1 = ClientPacket.Event("/")
    packet1.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    helpers.test(packet1)

    val packet2 = ClientPacket.Event("/")
    packet2.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    helpers.test(packet2)
  }

  @Test
  fun encodeAck() {
    val packet = ClientPacket.Ack("/")
    packet.payload = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
    packet.ackId = 123
    helpers.test(packet)
  }

  @Test
  fun decodeInError() {
    // Random string
    helpers.testDecodeError("asdf")
    // Unknown type
    helpers.testDecodeError(ClientParser.packetTypes.size.toString() + "asdf")
    // Binary event with no `-`
    helpers.testDecodeError(ClientParser.BINARY_EVENT.toString() + "asdf")
    // Binary ack with no `-`
    helpers.testDecodeError(ClientParser.BINARY_ACK.toString() + "asdf")
    // Binary event with no attachment
    helpers.testDecodeError(ClientParser.BINARY_EVENT.toString())
    // event non numeric id
    helpers.testDecodeError(ClientParser.EVENT.toString() + "2sd")
    // event with invalid json data
    helpers.testDecodeError(ClientParser.EVENT.toString() + "2[\"a\",1,{asdf}]")
    helpers.testDecodeError(ClientParser.EVENT.toString() + "2{}")
    // helpers.testDecodeError(ClientParser.EVENT.toString() + "2[]")
    helpers.testDecodeError(ClientParser.EVENT.toString() + "2[null]")
  }
}
