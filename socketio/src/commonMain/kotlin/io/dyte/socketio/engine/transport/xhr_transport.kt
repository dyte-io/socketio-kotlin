package io.dyte.socketio.engine.transport

import io.dyte.socketio.EventEmitter
import io.dyte.socketio.Logger
import io.dyte.socketio.engine.EngineSocket
import io.dyte.socketio.engine.Timer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.toMap
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.forEach
import kotlin.collections.getOrElse
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.toMap
import kotlin.collections.toUByteArray
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class XHRTransport : PollingTransport {
  // int? requestTimeout;
  var xd: Boolean = false
  var xs: Boolean = false
  var sendXhr: Request? = null
  var pollXhr: Request? = null

  /**
   * XHR Polling constructor.
   *
   * @param {Object} opts
   */
  constructor(opts: TransportOptions, socket: EngineSocket? = null) : super(opts, socket) {
    // requestTimeout = opts["requestTimeout"];
    if (opts.extraHeaders != null) extraHeaders = opts.extraHeaders!!
  }

  /** XHR supports binary */
  override var supportsBinary = true

  /** Creates a request. */
  fun request(opts: MutableMap<String, Any> = mutableMapOf<String, Any>()): Request {
    opts["uri"] = uri()
    opts["xd"] = xd
    opts["xs"] = xs
    opts["agent"] = if (agent) agent else false
    opts["supportsBinary"] = supportsBinary
    extraHeaders?.let { opts["extraHeaders"] = it }
    opts["requestTimeout"] = requestTimeout

    return Request(opts.toMap())
  }

  /**
   * Sends data.
   *
   * @param {String} data to send.
   * @param {Function} called upon flush.
   */
  override fun doWrite(data: String, fn: (data: Any?) -> Unit) {
    var isBinary = !(data is String)
    var req =
      request(mutableMapOf<String, Any>("method" to "POST", "data" to data, "isBinary" to isBinary))
    req.once("d", fn)
    req.once("success", fn)
    req.once("error", { err: Any? -> onError("xhr post error", err as String) })
    req.once(EVENT_RESPONSE_HEADERS, { resp -> emit(EVENT_RESPONSE_HEADERS, resp) })
    sendXhr = req
  }

  /** Starts a poll cycle. */
  override fun doPoll() {
    Logger.debug("xhr doing poll")
    var req = request()
    req.once("data", { data: Any? -> onData(data as String) })
    req.once("error", { err: Any? -> onError("xhr post error", err as String) })
    pollXhr = req
  }
}

/**
 * Request constructor
 *
 * @param {Object} options
 */
class Request : EventEmitter {
  val EVENT_SUCCESS = "success"
  val EVENT_DATA = "data"
  val EVENT_ERROR = "error"
  val EVENT_REQUEST_HEADERS = "requestHeaders"
  val EVENT_RESPONSE_HEADERS = "responseHeaders"

  var uri: String
  var xd: Boolean
  var xs: Boolean
  var async: Boolean
  var data: Any?
  var agent: Boolean? = null
  var isBinary: Boolean? = null
  var supportsBinary: Boolean? = null
  var requestTimeout: Long
  val client = HttpClient()
  var reqMethod: String
  var extraHeaders: Map<String, Any>?

  constructor(opts: Map<String, Any>) {
    reqMethod = opts.getOrElse("method") { "GET" } as String
    uri = opts["uri"] as String
    xd = opts["xd"] == true
    xs = opts["xs"] == true
    async = opts["async"] != false
    data = opts["data"]
    agent = opts["agent"] as Boolean?
    isBinary = opts["isBinary"] as Boolean?
    supportsBinary = opts["supportsBinary"] as Boolean?
    requestTimeout = opts["requestTimeout"] as Long
    extraHeaders = opts["extraHeaders"] as Map<String, Any>?

    create()
  }

  /** Creates the XHR object and sends the request. */
  fun create() {

    var self = this

    Logger.debug("xhr open $reqMethod: $uri")
    GlobalScope.launch {
      try {
        val resp =
          client.request(uri) {
            when (reqMethod) {
              "GET" -> method = HttpMethod.Get
              "POST" -> method = HttpMethod.Post
              "PUT" -> method = HttpMethod.Put
            }
            headers {
              append("Accept", "*/*")
              extraHeaders?.forEach { append(it.key, it.value as String) }
              if (reqMethod == "POST") {
                if (isBinary == true) {
                  append("Content-type", "application/octet-stream")
                } else {
                  append("Content-type", "text/plain;charset=UTF-8")
                }
              }
            }
            //          timeout { connectTimeoutMillis = requestTimeout }
            if (reqMethod == "POST") {
              setBody(data)
            }
          }

        if (resp.status == HttpStatusCode.OK || resp.status.value == 1223) {
          var respData: Any
          if (resp.contentType()?.contentType == "application/octet-stream") {
            respData = resp.body<ByteArray>()
          } else {
            respData = resp.bodyAsText()
          }

          onResponseHeaders(resp.headers.toMap())

          if (respData != null) {
            if (respData is ByteArray) respData = respData.toUByteArray()
            onData(respData)
          }
        } else {
          Timer(1, { -> onError(resp.status.value.toString()) }).schedule()
        }
      } catch (e: Exception) {
        onError("${e.message}")
        Logger.error("XHR Error", e)
        e.printStackTrace()
      }
    }
  }

  /** Called if we have data. */
  fun onData(data: Any) {
    emit("data", data)
    onSuccess()
  }

  fun onResponseHeaders(data: Map<String, List<String>>) {
    emit(EVENT_RESPONSE_HEADERS, data)
  }

  /** Called upon successful response. */
  fun onSuccess() {
    emit("success")
    cleanup()
  }

  /** Called upon error. */
  fun onError(err: String) {
    emit("error", err)
    cleanup(true)
  }

  /** Cleans up house. */
  fun cleanup(fromError: Boolean = false) {
    //        if (fromError != null) {
    //          try {
    //            xhr!.abort();
    //          } catch (e) {
    //            // ignore
    //          }
    //        }
    //        xhr = null;
  }

  /** Check if it has XDomainRequest. */
  fun hasXDR(): Boolean {
    return false
  }

  /** Aborts the request. */
  fun abort() {
    cleanup()
  }
}
