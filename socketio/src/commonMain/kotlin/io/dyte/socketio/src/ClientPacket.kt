package io.dyte.socketio.src

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray


/**
 * A Socket.IO packet, as defined in the [Socket.IO protocol](https://socket.io/docs/v4/socket-io-protocol).
 *
 * Exact payload types can be checked against the
 * [reference implementation](https://github.com/socketio/socket.io-parser/blob/87236baf87cdbe32ae01e7dc53320474520ce82f/lib/index.ts#L280).
 */
sealed class ClientPacket {

  /**
   * The namespace this packet belongs to, useful when multiplexing.
   * The default namespace is "/".
   */
  abstract var namespace: String

  /**
   * Used during the connection to a namespace.
   */
  data class Connect(
    override var namespace: String = "",
    /**
     * Since v5, CONNECT messages can have a payload as a JSON object.
     */
    val payload: JsonObject? = null,
  ) : ClientPacket()

  /**
   * Used during the connection to a namespace.
   */
  data class ConnectError(
    override var namespace: String,
    /**
     * A description of the error. It was a JSON string literal before v5, and is a JSON object since v5.
     */
    val errorData: JsonElement?,
  ) : ClientPacket()

  /**
   * Used when disconnecting from a namespace.
   */
  data class Disconnect(
    override var namespace: String = "",
  ) : ClientPacket()

  /**
   * A parent for packet types having a payload.
   */
  sealed class Message : ClientPacket() {
    /**
     * An ID used to match an [Event] with the corresponding [Ack].
     * When an [ackId] is present in an [Event] packet, it means an [Ack] packet is expected by the sender.
     */
    abstract val ackId: Int?

    /**
     * The payload of the message, which must be a non-empty array.
     * Usually the first element of the array is the event type (string or int), and the rest is the actual data.
     */
    abstract var payload: JsonArray
  }

  /**
   * Used to send data to the other side.
   */
  data class Event(
    override var namespace: String = "",
    override var ackId: Int? = null,
    override var payload: JsonArray = buildJsonArray {  },
  ): Message()

  /**
   * Used to send binary data to the other side.
   */
  data class BinaryEvent(
    override var namespace: String,
    override val ackId: Int?,
    override var payload: JsonArray,
    var attachments: Int?
  ): Message()

  /**
   * Used to acknowledge the event with the corresponding [ackId].
   */
  data class Ack(
    override var namespace: String = "",
    override var ackId: Int = 0,
    override var payload: JsonArray = buildJsonArray {  },
  ): Message()

  /**
   * Used to acknowledge the event with the corresponding [ackId].
   */
  data class BinaryAck(
    override var namespace: String,
    override val ackId: Int,
    override var payload: JsonArray,
    val attachments: Int?
  ): Message()

  fun toCharType(): Char {
    return when (this) {
      is Connect -> '0'
      is Disconnect -> '1'
      is Event -> '2'
      is Ack -> '3'
      is ConnectError -> '4'
      is BinaryEvent -> '5'
      is BinaryAck -> '6'
    }
  }

}
