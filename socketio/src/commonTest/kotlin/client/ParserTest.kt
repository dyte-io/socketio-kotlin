package client

import io.dyte.socketio.src.ClientPacket
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test


class ParserTest {
    @Test
    fun encodeConnection() {
        val packet: ClientPacket<*> = ClientPacket<Any?>(ClientParser.CONNECT)
        packet.nsp = "/woot"
        helpers.test(packet)
    }

    @Test
    fun encodeDisconnection() {
        val packet: ClientPacket<*> = ClientPacket<Any?>(ClientParser.DISCONNECT)
        packet.nsp = "/woot"
        helpers.test(packet)
    }

    @Test
    fun encodeEvent() {
        val packet1 = ClientPacket<JsonArray>(ClientParser.EVENT)
        packet1.data = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
        packet1.nsp = "/"
        helpers.test(packet1)
        val packet2 = ClientPacket<JsonArray>(ClientParser.EVENT)
        packet2.data = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
        packet2.nsp = "/test"
        helpers.test(packet2)
    }

    @Test
    fun encodeAck() {
        val packet = ClientPacket<JsonArray>(ClientParser.ACK)
        packet.data = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive(1)))
        packet.id = 123
        packet.nsp = "/"
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
        helpers.testDecodeError(ClientParser.EVENT.toString() + "2[]")
        helpers.testDecodeError(ClientParser.EVENT.toString() + "2[null]")
    }
}