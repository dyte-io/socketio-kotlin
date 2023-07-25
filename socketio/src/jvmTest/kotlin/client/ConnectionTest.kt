package client

import ACKFn
import SocketClient
import base.Connection
import io.dyte.socketio.IO
import io.dyte.socketio.IOOptions
import io.dyte.socketio.src.engine.asBoolean
import io.dyte.socketio.src.engine.asInt
import io.dyte.socketio.src.engine.asJsonObject
import io.dyte.socketio.src.engine.asString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class ConnectionTest : Connection("client") {

    fun client(): SocketClient {
        return client(createClientOptions())
    }

    fun client(path: String): SocketClient {
        return client(path, createClientOptions())
    }

    fun client(opts: IOOptions): SocketClient {
        return client(nsp(), opts)
    }

    fun client(path: String, opts: IOOptions): SocketClient {
        return IO.socket(uri() + path, opts)
    }

    fun uri(): String {
        return "http://localhost:" + PORT
    }

    fun nsp(): String {
        return "/"
    }

    fun createClientOptions(): IOOptions {
        val opts = IOOptions()
        opts.forceNew = true
        opts.transports = mutableListOf("polling")
        return opts
    }

    @Test(timeout = TIMEOUT.toLong())
    fun connectToLocalhost() {
        val values: BlockingQueue<Boolean> = LinkedBlockingQueue()
        var socket = client()
        socket.onConnect {
            socket.emit("echo")
            socket.on("echoBack") {
                values.offer(true)
            }
        }

        socket.open()
        assertTrue(values.take())
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    fun startTwoConnectionsWithSamePath() {
        val s1 = client("/")
        val s2 = client("/")
        assertNotEquals(s1.io, s2.io)
        s1.close()
        s2.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(InterruptedException::class)
    fun workWithAcks() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        val socket = client()
        socket.onConnect {
            socket.emit("callAck")
            socket.onEvent("ack", fun(data: Any?,ack: ACKFn) {
                val data = buildJsonObject {
                    put("test", JsonPrimitive(true))
                }
                ack?.invoke(listOf(JsonPrimitive(5), data))
            })
            socket.onEvent("ackBack", fun(args: ArrayList<JsonElement>) {
                try {
                    val data = args[1].asJsonObject()
                    if (args[0].asInt() == 5 && data?.get("test")?.asBoolean() == true) {
                        values.offer("done")
                    }
                } catch (e: Exception) {
                    throw AssertionError(e)
                }
            })
        }
        socket.connect()
        values.take()
        socket.close()
    }

    @Test(timeout = TIMEOUT.toLong())
    @Throws(InterruptedException::class)
    fun receiveDateWithAck() {
        val values: BlockingQueue<Any> = LinkedBlockingQueue()
        val socket = client()
        socket.onConnect {
                try {
                    socket.emit("getAckDate", Json.decodeFromString<JsonObject>("{\"test\": true}"), fun(_args: Any?) {
                            val args = _args as ArrayList<JsonElement>
                            values.offer(args[0].jsonPrimitive.asString())
                    })
                } catch (e: Exception) {

                }
        }
        socket.connect()
        assertTrue(values.take() is String)
        socket.close()
    }
}
