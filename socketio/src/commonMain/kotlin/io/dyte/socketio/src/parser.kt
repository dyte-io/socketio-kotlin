import io.dyte.socketio.src.ClientPacket
import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.isNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A socket.io Encoder instance
 *
 *
 */
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
   *
   */
  fun encode(obj: ClientPacket<*>): List<Any> {

    if (ClientParser.EVENT == obj.type || ClientParser.ACK == obj.type) {
      //      if (hasBinary(obj)) {
      //        obj["type"] = obj["type"] == EVENT ? BINARY_EVENT : BINARY_ACK;
      //        return encodeAsBinary(obj);
      //      }
    }
    return listOf(encodeAsString(obj))
  }

  companion object {

    /**
     * Encode packet as string.
     *
     * @param {Object} packet
     * @return {String} encoded
     *
     */
    fun encodeAsString(obj: ClientPacket<*>): String {
      // first is type
      var str = "${obj.type}"

      // attachments if we have them
      if (ClientParser.BINARY_EVENT == obj.type || ClientParser.BINARY_ACK == obj.type) {
        str += "${obj.attachments}-"
      }

      // if we have a namespace other than `/`
      // we append it followed by a comma `,`
      if (obj.nsp != null && "/" != obj.nsp) {
        str += obj.nsp as String + ","
      }

      // immediately followed by the id
      if (obj.id >= 0) {
        str += "${obj.id}"
      }

      // json data
      if (obj.data != null) {
        if (obj.data is JsonObject) {
          str += Json.encodeToString(obj.data as JsonObject)
        } else if (obj.data is JsonArray) {
          str += Json.encodeToString(obj.data as JsonArray)
        } else if (obj.data is List<*>) {
          str += Json.encodeToString(obj.data as List<JsonElement>)
        } else {
          Logger.warn("Error: encode type not found ${obj.data}")
        }
      }

      Logger.debug("encoded $obj as $str")
      return str
    }

    /**
     * Encode packet as "buffer sequence" by removing blobs, and deconstructing packet into object
     * with placeholders and a list of buffers.
     *
     * @param {Object} packet
     * @return {Buffer} encoded
     *
     */
    //    fun encodeAsBinary(obj: MutableMap<String,Any>): Buffer { // TODO:  BUFFER
    //      val deconstruction = Binary.deconstructPacket(obj);
    //      val pack = encodeAsString(deconstruction["packet"]);
    //      val buffers = deconstruction["buffers"];
    //
    //      // add packet info to beginning of data list
    //      return <dynamic>[pack]..addAll(buffers); // write all the buffers
    //    }

  }
}

/**
 * A socket.io Decoder instance
 *
 * @return {Object} decoder
 *
 */
class ClientDecoder : EventEmitter() {
  //  dynamic reconstructor = null;

  /**
   * Decodes an ecoded packet string into packet JSON.
   *
   * @param {String} obj - encoded packet
   * @return {Object} packet
   *
   */
  fun add(obj: Any) {
    var packet: ClientPacket<Any>
    if (obj is String) {
      packet = decodeString(obj)
      if (ClientParser.BINARY_EVENT == packet.type || ClientParser.BINARY_ACK == packet.type) {
        // binary packet"s json
        //        this.reconstructor = new BinaryReconstructor(packet);
        //
        //        // no attachments, labeled binary but no binary data to follow
        //        if (this.reconstructor.reconPack["attachments"] == 0) {
        //          this.emit("decoded", packet);
        //        }
      } else {
        // non-binary full packet
        this.emit("decoded", packet)
      }
      //    } else if (isBinary(obj) || obj is Map && obj["base64"] != null) {
      //      // raw binary data
      //      if (this.reconstructor == null) {
      //        throw UnsupportedOperationException(
      //            "got binary data when not reconstructing a packet");
      //      } else {
      //        packet = this.reconstructor.takeBinaryData(obj);
      //        if (packet != null) {
      //          // received final buffer
      //          this.reconstructor = null;
      //          this.emit("decoded", packet);
      //        }
      //      }
      //    } else {
      // throw UnsupportedOperationException("Unknown type: " + obj);
    }
  }

  companion object {
    /**
     * Decode a packet String (JSON data)
     *
     * @param {String} str
     * @return {Object} packet
     *
     */
    fun decodeString(str: String): ClientPacket<Any> {
      var i = 0
      var endLen = str.length - 1
      // look up type
      val type = str[0].digitToInt()
      var p = ClientPacket<Any>(type)

      if (null == ClientParser.packetTypes[type]) {
        throw UnsupportedOperationException("unknown packet type " + p.type)
      }

      // look up attachments if type binary
      if (ClientParser.BINARY_EVENT == p.type || ClientParser.BINARY_ACK == p.type) {
        if (!str.contains("-") || str.length <= i + 1) {
          throw UnsupportedOperationException("illegal attachments")
        }
        var attachments = ""
        while (str[++i] != '-') {
          attachments += str[i]
        }
        p.attachments = attachments.toInt()
      }

      // look up namespace (if any)
      if (i < endLen - 1 && '/' == str[i + 1]) {
        var start = i + 1
        while (++i > 0) {
          if (i == str.length) break
          var c = str[i]
          if (',' == c) break
        }
        p.nsp = str.substring(start, i)
      } else {
        p.nsp = "/"
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
        p.id = str.substring(start, i + 1).toInt()
      }

      // look up json data
      if (i < endLen - 1 && str[++i].toString().isNotEmpty() == true) {
        var payload = tryParse(p.type, str.substring(i))
        p.data = payload
        if (isPayloadValid(p.type, p.data as Any).not()) {
          throw UnsupportedOperationException("invalid payload")
        }
      }

      return p
    }

    fun tryParse(type: Int, str: String): Any {
      try {
        when (type) {
          ClientParser.EVENT,
          ClientParser.ACK -> return Json.decodeFromString<JsonArray>(str)
          else -> return Json.decodeFromString<JsonObject>(str)
        }
      } catch (e: Exception) {
        Logger.error("Socket parser. JSON Error", e)
      }
      return mapOf<String, JsonElement>()
    }

    fun isPayloadValid(type: Int, payload: Any): Boolean {
      when (type) {
        ClientParser.CONNECT -> return payload is JsonObject
        ClientParser.DISCONNECT -> return payload == null
        ClientParser.CONNECT_ERROR -> return payload is JsonObject
        ClientParser.EVENT,
        ClientParser.BINARY_EVENT ->
          return payload is JsonArray && payload.size > 0 && payload.getOrNull(0)?.isNull() == false
        ClientParser.ACK,
        ClientParser.BINARY_ACK -> return payload is JsonArray
      }
      return false
    }

    /**
     * Deallocates a parser"s resources
     *
     *
     */
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
 *
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
