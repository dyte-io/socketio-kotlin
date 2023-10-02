package client

import ClientDecoder
import ClientEncoder
import io.dyte.socketio.src.ClientPacket
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.assertFailsWith

class helpers {
    companion object {
        val encoder = ClientEncoder()

        fun test(obj: ClientPacket) {
            val encodedPackets = encoder.encode(obj)
            val decoder = ClientDecoder()
            decoder.on("decoded", fun(p: Any?) {
                val p = p as ClientPacket
                assertPacket(obj, p)
            })
            decoder.add(encodedPackets[0])
        }

        fun testDecodeError(errorMessage: String) {
            val decoder = ClientDecoder()

            assertFailsWith<Exception> {
                decoder.add(errorMessage)
            }
        }

        fun testBin(obj: ClientPacket.BinaryEvent) {
            val originalData = obj.payload
            val encodedPackets = encoder.encode(obj)
            val decoder = ClientDecoder()
            decoder.on("decoded", fun(p: Any?) {
                val p = p as ClientPacket
                obj.payload = originalData
                obj.attachments = -1
                assertPacket(obj, p)
            })

//            encodedPackets.forEach { packet ->
//                if (packet is String) {
//                    decoder.add(packet)
//                } else if (packet is ByteArray) {
//                    decoder.add(packet)
//                }
//            }
        }

        fun assertPacket(expected: ClientPacket, actual: ClientPacket) {
            assertEquals(expected.toCharType(), actual.toCharType())
            assertEquals(expected.namespace, actual.namespace)
            if(expected is ClientPacket.BinaryEvent && actual is ClientPacket.BinaryEvent) {
                assertEquals(expected.attachments, actual.attachments)
            }

            if (expected is ClientPacket.Message && actual is ClientPacket.Message) {
                assertEquals(expected.ackId, actual.ackId)

                assertEquals(
                    expected.payload,
                    actual.payload,
                )
            }
        }
    }
}
