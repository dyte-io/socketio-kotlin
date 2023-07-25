import io.dyte.socketio.src.engine.EnginePacket
import io.ktor.util.*

class PacketParser {

    companion object {
        val protocol = 4;

        enum class PacketType { OPEN, CLOSE, PING, PONG, MESSAGE, UPGRADE, NOOP }

        val packetTypes: List<String> = listOf(
            "open",
            "close",
            "ping",
            "pong",
            EnginePacket.MESSAGE,
            "upgrade",
            "noop"
        );


        val SEPARATOR = (30).toChar();
        val PacketTypeMap: Map<String, Int> = mapOf(
            "open" to 0,
            "close" to 1,
            "ping" to 2,
            "pong" to 3,
            EnginePacket.MESSAGE to 4,
            "upgrade" to 5,
            "noop" to 6
        );
        val ERROR_PACKET = EnginePacket(EnginePacket.ERROR, "parser error");

        fun <T> encodePacket(
            packet: EnginePacket<T>,
            supportsBinary: Boolean,
            utf8encode: Any = false,
            callback: (data: Any) -> Unit,
            fromClient: Boolean = false
        ): Unit {
            // TODO: DYTE
            //    if (supportsBinary is Function<*>) {
            //      callback = supportsBinary as dynamic Function(dynamic);
//        supportsBinary = null;
//    }
//
//    if (utf8encode is Function<*>) {
//      callback = utf8encode as Function;
//      utf8encode = null;
//    }

            if (packet.data != null) {
                if (packet.data is UIntArray) {
                    return encodeBuffer(
                        packet, supportsBinary, callback,
                        fromClient
                    );
                } else if (packet.data is Map<*, *> &&
                    ((packet.data as Map<String, Any>)["buffer"] != null &&
                            (packet.data as Map<String, Any>)["buffer"] is ByteArray)
                ) {
                    val newPacket = EnginePacket(
                        packet.type,
                        ((packet.data as Map<String, Any>)["buffer"] as ByteArray).toUByteArray()
                    )
                    return encodeBuffer(
                        newPacket, supportsBinary, callback,
                        fromClient
                    );
                } else if (packet.data is ByteArray) {
                    return encodeBuffer(
                        packet, supportsBinary, callback,
                        fromClient
                    );
                }
            }

            // Sending data as a utf-8 string
            var encoded = PacketTypeMap[packet.type].toString();

            // data fragment is optional
            if (packet.data != null) {
                encoded += packet.data.toString()
                // TOOD: utf8 encode
                // else """${packet.data}""";
            }

            callback("$encoded");
        }

        /**
         * Encode Buffer data
         */

        fun <T> encodeBuffer(
            packet: EnginePacket<T>, supportsBinary: Boolean, callback: (data: Any) -> Unit,
            fromClient: Boolean = false /*use this to check whether is in client or not*/
        ) {
            if (!supportsBinary) {
                return encodeBase64Packet(packet, callback);
            }

            var data = packet.data;
            // "fromClient" is to check if the runtime is on server side or not,
            // because Dart server"s websocket cannot send data with byte buffer.
            if (data is String) {
                return callback((data as Map<String, Any>)["buffer"] as String);
            } else {
                return callback(data as ByteArray);
            }
        }

        /**
         * Encodes a packet with binary data in a base64 string
         *
         * @param {Object} packet, has `type` and `data`
         * @return {String} base64 encoded message
         */

        fun <T> encodeBase64Packet(packet: EnginePacket<T>, callback: (data: String) -> Unit) {
            var message = "b";
            var barr = packet.data as ByteArray
            message += barr.encodeBase64()
            callback(message)
        }

        fun mapBinary(data: Any, binaryType: String?): Any {
            // TODO
            //    val isBuffer = data is ByteArray;
            //    if (binaryType == "arraybuffer") {
            //      return if (isBuffer) UIntArray fromList(data) else data;
            //    }
            return data;
        }

        fun decodePacket(encodedPacket: Any?): EnginePacket<*> {
            if (encodedPacket == null) {
                return ERROR_PACKET;
            }
            if ((encodedPacket is String) == false) {
                return EnginePacket(
                    EnginePacket.MESSAGE, encodedPacket
                );
            }


            if (encodedPacket.length > 0 && encodedPacket[0] == 'b') {
                var decoded = encodedPacket.substring(1).decodeBase64Bytes()
                return EnginePacket(EnginePacket.MESSAGE, decoded)
            }
            var typeNumber: Int;
            try {
                var type = encodedPacket[0];
                typeNumber = type.digitToInt();
            } catch (e: Exception) {
                typeNumber = -1;
            }

            if (typeNumber < 0 || typeNumber >= packetTypes.size) {
                return ERROR_PACKET;
            }

            var pt = packetTypes[typeNumber];

            if (encodedPacket.length > 1) {
                return EnginePacket(
                    pt,
                    encodedPacket.substring(1)
                );
            } else {
                return EnginePacket<Any>(pt);
            }
        }

        fun encodePayload(packets: List<EnginePacket<*>>, callback: (String) -> Unit) {
            val length = packets.size;
            val encodedPackets = mutableListOf<Any>()
            var count = 0;
            packets.forEach {
                val packet = it;
                // force base64 encoding for binary packets
                encodePacket(packet, false, callback = fun(encodedPacket) {
                    encodedPackets.add(encodedPacket);
                    if (encodedPackets.size == length) {
                        callback(encodedPackets.joinToString(SEPARATOR.toString()));
                    }
                });

            }
        }

        /*
         * Decodes data when a payload is maybe expected. Possible binary contents are
         * decoded from their base64 representation
         *
         * @param {String} data, callback method
         * @api public
         */
        fun decodePayload(
            encodedPayload: String,
        ): MutableList<EnginePacket<*>> {
            var encodedPackets = encodedPayload.split(SEPARATOR);
            var packets = mutableListOf<EnginePacket<*>>();
            for (i in 0..(encodedPackets.size - 1)) {
                var decodedPacket = decodePacket(encodedPackets[i]);
                packets.add(decodedPacket);
                if (decodedPacket.type == EnginePacket.ERROR) {
                    break;
                }
            }
            return packets;
        }
    }
}
