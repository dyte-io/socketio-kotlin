package io.dyte.socketio.src.engine

import kotlinx.serialization.Serializable

@Serializable
sealed class EnginePacket {

  /** Used during the handshake. */
  @Serializable
  data class Open(
    /** The session ID. */
    val sid: String,
    /** The list of available transport upgrades. */
    val upgrades: List<String>,
    /** The ping interval, used in the heartbeat mechanism (in milliseconds). */
    val pingInterval: Long,
    /** The ping timeout, used in the heartbeat mechanism (in milliseconds). */
    val pingTimeout: Long,
    /**
     * Optional, only useful when using long-polling as transport to know how many packets to batch
     * together.
     */
    val maxPayload: Long? = null,
  ) : EnginePacket()

  /** Used to indicate that a transport can be closed. */
  @Serializable data object Close : EnginePacket()

  /** Used during the upgrade process. */
  @Serializable data object Upgrade : EnginePacket()

  /** Used during the upgrade process. */
  @Serializable data object Noop : EnginePacket()

  /** Used in the heartbeat mechanism. */
  @Serializable data class Ping(val payload: String? = null) : EnginePacket()

  /** Used in the heartbeat mechanism. */
  @Serializable data class Pong(val payload: String? = null) : EnginePacket()

  /** Used to send a payload to the other side. */
  @Serializable data class Message(val payload: String? = null) : EnginePacket()

  /** Used to send a binary payload to the other side. */
  @Serializable data class BinaryMessage(val payload: ByteArray) : EnginePacket()

  /** [Internal use only] */
  @Serializable data class Error(val payload: String) : EnginePacket()

  fun toCharType(): Char {
    return when (this) {
      is Open -> '0'
      is Close -> '1'
      is Ping -> '2'
      is Pong -> '3'
      is Message -> '4'
      is BinaryMessage -> '4'
      is Upgrade -> '5'
      is Noop -> '6'
      is Error -> error("No char type for error")
    }
  }
}
