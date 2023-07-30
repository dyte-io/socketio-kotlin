package io.dyte.socketio.src

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object utils {
    fun handlePrimitive(l: MutableList<JsonElement>, it: Any?) {
        if(it is String) {
            l.add(JsonPrimitive(it));
        } else if (it is Boolean) {
            l.add(JsonPrimitive(it));
        } else if (it is Number) {
            l.add(JsonPrimitive(it));
        } else if (it is JsonElement) {
            l.add(it);
        }
    }
}