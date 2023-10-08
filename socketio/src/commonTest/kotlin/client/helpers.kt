package client

import ClientParser
import io.dyte.socketio.src.ClientPacket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

object helpers {

  fun test(obj: ClientPacket) {
    val encodedPacket = ClientParser.encode(obj)
    val packet = ClientParser.decode(encodedPacket)
    assertPacket(obj, packet)
  }

  fun testDecodeError(errorMessage: String) {
    assertFailsWith<Exception> { ClientParser.decode(errorMessage) }
  }

  fun assertPacket(expected: ClientPacket, actual: ClientPacket) {
    assertEquals(expected.toCharType(), actual.toCharType())
    assertEquals(expected.namespace, actual.namespace)
    if (expected is ClientPacket.BinaryEvent && actual is ClientPacket.BinaryEvent) {
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
