import io.dyte.socketio.src.ClientPacket
import io.dyte.socketio.src.Logger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A socket.io Encoder instance */
object ClientParser {
  const val CONNECT = 0
  const val DISCONNECT = 1
  const val EVENT = 2
  const val ACK = 3
  const val CONNECT_ERROR = 4
  const val BINARY_EVENT = 5
  const val BINARY_ACK = 6

  val packetTypes: List<String> =
    listOf("CONNECT", "DISCONNECT", "EVENT", "ACK", "CONNECT_ERROR", "BINARY_EVENT", "BINARY_ACK")

  /**
   * Encode [ClientPacket] as string.
   *
   * @param {Object} packet
   * @return {String} encoded
   */
  fun encode(obj: ClientPacket): String {
    // first is type
    var str = "${obj.toCharType()}"

    // attachments if we have them
    if (obj is ClientPacket.BinaryEvent) {
      str += "${obj.attachments}-"
    }
    if (obj is ClientPacket.BinaryAck) {
      str += "${obj.attachments}-"
    }

    // if we have a namespace other than `/`
    // we append it followed by a comma `,`
    if (obj.namespace != "/") {
      str += obj.namespace + ","
    }

    if (obj is ClientPacket.Message) {
      // immediately followed by the id
      obj.ackId?.let { str += "${obj.ackId}" }
      str += Json.encodeToString(obj.payload)
    }

    Logger.debug("encoded $obj as $str")
    return str
  }

  /**
   * Decodes an encoded packet string into [ClientPacket]
   *
   * @param {String} str
   * @return {Object} packet
   */
  fun decode(str: String): ClientPacket {
    var i = 0
    val endLen = str.length - 1
    // look up type
    val type = str[0].digitToInt()
    if (type < 0 || type > 6) {
      throw UnsupportedOperationException("Unknown Socket.IO packet type $type")
    }

    var packetNamespace = ""
    var packetId: Int? = null
    var packetPayload: JsonElement? = null
    var packetBinaryAttachments: Int? = null

    // look up attachments if type binary
    if (BINARY_EVENT == type || BINARY_ACK == type) {
      if (!str.contains("-") || str.length <= i + 1) {
        throw UnsupportedOperationException("illegal attachments")
      }
      var attachments = ""
      while (str[++i] != '-') {
        attachments += str[i]
      }
      packetBinaryAttachments = attachments.toInt()
    }

    // look up namespace (if any)
    if (i < endLen - 1 && '/' == str[i + 1]) {
      val start = i + 1
      while (++i > 0) {
        if (i == str.length) break
        val c = str[i]
        if (',' == c) break
      }
      packetNamespace = str.substring(start, i)
    } else {
      packetNamespace = "/"
    }

    // look up id
    val next: String? = if (i < endLen - 1) str[i + 1].toString() else null
    if (next?.isNotEmpty() == true && "${next.toIntOrNull()}" == next) {
      val start = i + 1
      while (++i > 0) {
        val c: String? = if (str.length > i) str[i].toString() else null
        try {
          if ("${c?.toInt()}" != c) {
            --i
            break
          }
        } catch (e: Exception) {
          --i
          break
        }
        if (i == str.length) break
      }
      packetId = str.substring(start, i + 1).toInt()
    }

    // look up json data
    if (i < endLen - 1 && str[++i].toString().isNotEmpty()) {
      val payload = tryParse(type, str.substring(i))
      packetPayload = payload
    }
    val packet =
      when (type) {
        0 -> ClientPacket.Connect(packetNamespace, packetPayload as JsonObject?)
        1 -> ClientPacket.Disconnect(packetNamespace)
        2 -> ClientPacket.Event(packetNamespace, packetId, packetPayload as JsonArray)
        3 -> ClientPacket.Ack(packetNamespace, packetId!!, packetPayload as JsonArray)
        4 -> ClientPacket.ConnectError(packetNamespace, packetPayload as JsonObject?)
        5 ->
          ClientPacket.BinaryEvent(
            packetNamespace,
            packetId,
            packetPayload as JsonArray,
            packetBinaryAttachments
          )
        6 ->
          ClientPacket.BinaryAck(
            packetNamespace,
            packetId!!,
            packetPayload as JsonArray,
            packetBinaryAttachments
          )
        else -> throw UnsupportedOperationException("Unknown Socket.IO packet type $type")
      }
    return packet
  }

  fun tryParse(type: Int, str: String): JsonElement? {
    try {
      return when (type) {
        CONNECT,
        CONNECT_ERROR -> Json.decodeFromString<JsonObject?>(str)
        BINARY_ACK,
        BINARY_EVENT,
        EVENT,
        ACK -> Json.decodeFromString<JsonArray>(str)
        else -> throw UnsupportedOperationException("invalid payload")
      }
    } catch (e: Exception) {
      Logger.error("Socket parser. JSON Error", e)
    }
    return null
  }
}
/**
 * A manager of a binary event"s "buffer sequence". Should be constructed whenever a packet of type
 * ClientParser.BINARY_EVENT is decoded.
 *
 * @param {Object} packet
 * @return {BinaryReconstructor} initialized reconstructor
 */
// class BinaryReconstructor {
//  Map? reconPack;
//  List buffers = [];
//  BinaryReconstructor(packet) {
//    this.reconPack = packet;
//  }
//
//  /**
//   * Method to be called when binary data received from connection
//   * after a ClientParser.BINARY_EVENT packet.
//   *
//   * @param {Buffer | ArrayBuffer} binData - the raw binary data received
//   * @return {null | Object} returns null if more binary data is expected or
//   *   a reconstructed packet object if all buffers have been received.
//   *
//   */
//  takeBinaryData(binData) {
//    this.buffers.add(binData);
//    if (this.buffers.length == this.reconPack!["attachments"]) {
//      // done with buffer list
//      var packet = Binary.reconstructPacket(
//          this.reconPack!, this.buffers.cast<List<int>>());
//      this.finishedReconstruction();
//      return packet;
//    }
//    return null;
//  }
//
//  /** Cleans up binary packet reconstruction variables.
//   *
//   *
//   */
//  void finishedReconstruction() {
//    this.reconPack = null;
//    this.buffers = [];
//  }
// }
