package client

import ClientDecoder
import ClientEncoder
import io.dyte.socketio.src.ClientPacket
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertEquals

import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class helpers {
    companion object {
        val encoder = ClientEncoder()
        fun test(obj: ClientPacket<*>) {
            val encodedPackets = encoder.encode(obj)
            val decoder = ClientDecoder();
            decoder.on("decoded", fun(p: Any?) {
                val p = p as ClientPacket<*>;
                assertPacket(obj, p);
            });
            println("sdfsdfSDF::" + encodedPackets.size + "::" + encodedPackets[0])
            decoder.add(encodedPackets[0]);
        }


        fun testDecodeError(errorMessage: String) {
            val decoder = ClientDecoder();
            try {
                decoder.add(errorMessage);
                assertTrue(false)
            } catch (e: Exception) {

            }
        }

        fun testBin(obj: ClientPacket<Any>) {
            val originalData = obj.data;
            val encodedPackets = encoder.encode(obj)
            val decoder = ClientDecoder();
            decoder.on("decoded", fun(p: Any?) {
                val p = p as ClientPacket<Any>;
                obj.data = originalData;
                obj.attachments = -1;
                assertPacket(obj, p);
            })


            encodedPackets.forEach { packet ->
                if (packet is String) {
                    decoder.add(packet);
                } else if (packet is ByteArray) {
                    decoder.add(packet);
                }
            }
        }

        fun assertPacket(expected: ClientPacket<*>, actual: ClientPacket<*>) {
            assertEquals(expected.type, actual.type);
            assertEquals(expected.id, actual.id);
            assertEquals(expected.nsp, actual.nsp);
            assertEquals(expected.attachments, actual.attachments);

            if (expected.data is JsonArray) {

                println("sdfsdfsdfdsfsdf")
                println(expected.data)
                println(actual.data)

                assertEquals(
                    (expected.data as JsonArray),actual.data,
                );

            } else if (expected.data is JsonObject) {

                println("sdfsdfsdfdsfsd2f22")
                println(expected.data)
                println(actual.data)

                assertEquals((expected.data as JsonObject),actual.data)

            } else {
                assertEquals(expected.data ?: mapOf<Any, Any>(), actual.data ?: mapOf<Any, Any>());
            }
        }
    }
}