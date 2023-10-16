package io.dyte.socketio.engine

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object EnginePacketParser {

  val protocol = 4

  val SEPARATOR = (30).toChar()

  val ERROR_PACKET = EnginePacket.Error("parser error")

  @OptIn(ExperimentalEncodingApi::class)
  fun encodePacket(packet: EnginePacket): String {

    val packetType = "${packet.toCharType()}"
    return when (packet) {
      is EnginePacket.Message -> {
        return packetType + (packet.payload ?: "")
      }
      is EnginePacket.Ping -> {
        return packetType + (packet.payload ?: "")
      }
      is EnginePacket.Pong -> {
        return packetType + (packet.payload ?: "")
      }
      is EnginePacket.BinaryMessage -> {
        return "b" + Base64.encode(packet.payload)
      }
      else -> packetType
    }
  }

  fun decodePacket(encodedPacket: ByteArray): EnginePacket {
    return EnginePacket.BinaryMessage(encodedPacket)
  }

  @OptIn(ExperimentalEncodingApi::class)
  fun decodePacket(encodedPacket: String?): EnginePacket {
    if (encodedPacket == null || encodedPacket.isEmpty()) {
      return ERROR_PACKET
    }

    var payload: String? = encodedPacket.drop(1)
    if (payload?.isEmpty() == true) {
      payload = null
    }
    return when (val packetType = encodedPacket[0]) {
      '0' -> Json.decodeFromString<EnginePacket.Open>(payload!!)
      '1' -> EnginePacket.Close
      '2' -> EnginePacket.Ping(payload = payload)
      '3' -> EnginePacket.Pong(payload = payload)
      '4' -> EnginePacket.Message(payload = payload)
      '5' -> EnginePacket.Upgrade
      '6' -> EnginePacket.Noop
      'b' -> EnginePacket.BinaryMessage(payload = Base64.decode(payload!!))
      else -> error("Unknown Engine.IO packet type $packetType")
    }
  }

  fun encodeMultiplePacket(packets: List<EnginePacket>): String {
    if (packets.size == 0) return "0:"
    return packets.map { encodePacket(it) }.joinToString(SEPARATOR.toString())
  }

  /*
   * This is meant to be used in long-polling mode, where packets are batched in a single response.
   * When using web sockets, use [deserializePacket] instead.
   *
   * @param {String} data
   */
  fun decodeMultiplePacket(
    encodedPayload: String,
  ): MutableList<EnginePacket> {
    val encodedPackets = encodedPayload.split(SEPARATOR)
    val packets = mutableListOf<EnginePacket>()
    for (element in encodedPackets) {
      val decodedPacket = decodePacket(element)
      packets.add(decodedPacket)
      if (decodedPacket is EnginePacket.Error) {
        break
      }
    }
    return packets
  }
}
