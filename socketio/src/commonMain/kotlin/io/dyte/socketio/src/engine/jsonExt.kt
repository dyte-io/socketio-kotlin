package io.dyte.socketio.src.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonElement.asJsonObject(): JsonObject? {
  try {
    return this.jsonObject
  } catch (e: Exception) {
    return null
  }
}

fun JsonElement.asInt(): Int? {
  try {
    return this.jsonPrimitive.intOrNull
  } catch (e: Exception) {
    return null
  }
}

fun JsonElement.asBoolean(): Boolean? {
  try {
    return this.jsonPrimitive.booleanOrNull
  } catch (e: Exception) {
    return null
  }
}

fun JsonElement.asString(): String? {
  try {
    return this.jsonPrimitive.content
  } catch (e: Exception) {
    return null
  }
}

fun JsonElement.asJsonArray(): JsonArray? {
  try {
    return this.jsonArray
  } catch (e: Exception) {
    return null
  }
}

fun JsonElement.isNull(): Boolean {
  try {
    this.jsonNull
    return true
  } catch (e: Exception) {
    return false
  }
}
