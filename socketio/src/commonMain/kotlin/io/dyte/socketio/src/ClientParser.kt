import io.dyte.socketio.src.ClientPacket
import io.dyte.socketio.src.Logger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A socket.io Encoder instance */
class ClientParser {
  companion object {
    val CONNECT = 0
    val DISCONNECT = 1
    val EVENT = 2
    val ACK = 3
    val CONNECT_ERROR = 4
    val BINARY_EVENT = 5
    val BINARY_ACK = 6

    val packetTypes: List<String> =
      listOf("CONNECT", "DISCONNECT", "EVENT", "ACK", "CONNECT_ERROR", "BINARY_EVENT", "BINARY_ACK")
  }
}

class ClientEncoder {

  /**
   * Encode a packet as a single string if non-binary, or as a buffer sequence, depending on packet
   * type.
   *
   * @param {Object} obj - packet object
   * @param {Function} callback - function to handle encodings (likely engine.write)
   * @return Calls callback with Array of encodings
   */
  fun encode(obj: ClientPacket): List<String> {
    return listOf(encodeAsString(obj))
  }

  companion object {

    /**
     * Encode packet as string.
     *
     * @param {Object} packet
     * @return {String} encoded
     */
    fun encodeAsString(obj: ClientPacket): String {
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
  }
}

/**
 * A socket.io Decoder instance
 *
 * @return {Object} decoder
 */
class ClientDecoder : EventEmitter() {

  /**
   * Decodes an ecoded packet string into packet JSON.
   *
   * @param {String} obj - encoded packet
   * @return {Object} packet
   */
  fun add(obj: String) {
    var packet = decodeString(obj)
    this.emit("decoded", packet)
  }

  companion object {
    /**
     * Decode a packet String (JSON data)
     *
     * @param {String} str
     * @return {Object} packet
     */
    fun decodeString(str: String): ClientPacket {
      var i = 0
      var endLen = str.length - 1
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
      if (ClientParser.BINARY_EVENT == type || ClientParser.BINARY_ACK == type) {
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
        var start = i + 1
        while (++i > 0) {
          if (i == str.length) break
          var c = str[i]
          if (',' == c) break
        }
        packetNamespace = str.substring(start, i)
      } else {
        packetNamespace = "/"
      }

      // look up id
      var next: String? = if (i < endLen - 1) str[i + 1].toString() else null
      if (next?.isNotEmpty() == true && "${next.toIntOrNull()}" == next) {
        var start = i + 1
        while (++i > 0) {
          var c: String? = if (str.length > i) str[i].toString() else null
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
        var payload = tryParse(type, str.substring(i))
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
          ClientParser.CONNECT,
          ClientParser.CONNECT_ERROR -> Json.decodeFromString<JsonObject?>(str)
          ClientParser.BINARY_ACK,
          ClientParser.BINARY_EVENT,
          ClientParser.EVENT,
          ClientParser.ACK -> Json.decodeFromString<JsonArray>(str)
          else -> throw UnsupportedOperationException("invalid payload")
        }
      } catch (e: Exception) {
        Logger.error("Socket parser. JSON Error", e)
      }
      return null
    }

    /** Deallocates a parser"s resources */
    fun destroy() {
      //    if (this.reconstructor != null) {
      //      this.reconstructor.finishedReconstruction();
      //    }
    }
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
