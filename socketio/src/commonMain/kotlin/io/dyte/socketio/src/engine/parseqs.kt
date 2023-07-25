import io.ktor.http.*

fun encode(obj: List<Pair<String, String>>): String {
  return obj.formUrlEncode();
}

fun decode(qs: String): Parameters {
  return qs.parseUrlEncodedParameters();
}
