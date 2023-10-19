package io.dyte.socketio.engine

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.http.parseUrlEncodedParameters

fun encode(obj: List<Pair<String, String>>): String {
  return obj.formUrlEncode()
}

fun decode(qs: String): Parameters {
  return qs.parseUrlEncodedParameters()
}
