package io.dyte.socketio.src.engine.types

import kotlinx.serialization.Serializable

@Serializable
data class HandshakeModel (
    val sid: String,
    val upgrades: List<String>,
    val pingInterval: Long,
    val pingTimeout: Long,
    val maxPayload: Long
)