package engine

import decode
import encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseQSTest {
  @Test
  fun decodeTest() {
    var queryObject = decode("foo=bar")
    assertTrue(queryObject["foo"].equals("bar"))
    queryObject = decode("france=paris&germany=berlin")
    assertTrue(queryObject["france"].equals("paris"))
    assertTrue(queryObject["germany"].equals("berlin"))
    queryObject = decode("india=new%20delhi")
    assertTrue(queryObject["india"].equals("new delhi"))
    queryObject = decode("woot=")
    assertTrue(queryObject["woot"].equals(""))
    queryObject = decode("woot")
    assertTrue(queryObject["woot"].equals(""))
  }

  @Test
  fun encodeTest() {
    var obj = listOf("a" to "b")
    assertEquals(encode(obj), "a=b")
    obj = listOf("a" to "b", "c" to "d")
    assertEquals(encode(obj), "a=b&c=d")
    obj = listOf("a" to "b", "c" to "tobi rocks")
    assertEquals(encode(obj), "a=b&c=tobi+rocks")
  }
}
