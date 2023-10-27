import io.dyte.socketio.Backoff
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackoffTest {
  @Test
  fun durationShouldIncreaseTheBackoff() {
    val b = Backoff()
    assertTrue(100L == b.duration)
    assertTrue(200L == b.duration)
    assertTrue(400L == b.duration)
    assertTrue(800L == b.duration)
    b.reset()
    assertTrue(100L == b.duration)
    assertTrue(200L == b.duration)
  }

  @Test
  fun ensureJitterIsValid() {
    assertFailsWith<IllegalArgumentException> { Backoff(jitter = 2.0) }
  }
}
