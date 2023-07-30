import io.dyte.socketio.src.Logger
import io.dyte.socketio.src.engine.Timer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.toMap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.forEach
import kotlin.collections.getOrElse
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.toMap
import kotlin.collections.toUByteArray

class XHRTransport: PollingTransport {
  // int? requestTimeout;
  var xd: Boolean = false;
  var xs: Boolean = false;
  var sendXhr:Request? = null;
  var pollXhr: Request? = null;

  /**
   * XHR Polling constructor.
   *
   * @param {Object} opts
   * @api public
  */
  constructor(opts: TransportOptions, socket: EngineSocket? = null) : super(opts, socket) {
    // requestTimeout = opts["requestTimeout"];
    if(opts.extraHeaders != null) extraHeaders = opts.extraHeaders!!;
  }

  /**
   * XHR supports binary
  */
  override var supportsBinary = true;

  /**
   * Creates a request.
   *
   * @api private
  */
  fun request(opts: MutableMap<String,Any> = mutableMapOf<String,Any>()): Request {
    opts["uri"] = uri();
    opts["xd"] = xd;
    opts["xs"] = xs;
    opts["agent"] = if (agent) agent else false;
    opts["supportsBinary"] = supportsBinary;
    extraHeaders?.let {
      opts["extraHeaders"] = it;
    }
    opts["requestTimeout"] = requestTimeout;

    return Request(opts.toMap());
  }

  /**
   * Sends data.
   *
   * @param {String} data to send.
   * @param {Function} called upon flush.
   * @api private
  */
  override fun doWrite(data: String, fn: (data: Any?) -> Unit) {
    var isBinary = !(data is String);
    var req = request(mutableMapOf<String,Any>("method" to "POST", "data" to data, "isBinary" to isBinary));
    req.once("d", fn)
    req.once("success", fn);
    req.once("error", fun (err: Any?) {
      onError("xhr post error", err as String);
    });
    req.once(EVENT_RESPONSE_HEADERS, fun(resp) {
      emit(EVENT_RESPONSE_HEADERS, resp)
    })
    sendXhr = req;
  }

  /**
   * Starts a poll cycle.
   *
   * @api private
  */
  override fun doPoll() {
    Logger.fine("xhr poll");
    var req = request();
    req.once("data", fun (data: Any?) {
      onData(data as String);
    });
    req.once("error", fun (err: Any?) {
      onError("xhr post error", err as String);
    });
    pollXhr = req;
  }
}

/**
 * Request constructor
 *
 * @param {Object} options
 * @api public
*/
class Request: EventEmitter {
  val EVENT_SUCCESS = "success"
  val EVENT_DATA = "data"
  val EVENT_ERROR = "error"
  val EVENT_REQUEST_HEADERS = "requestHeaders"
  val EVENT_RESPONSE_HEADERS = "responseHeaders"

  var uri: String;
  var xd: Boolean;
  var xs: Boolean;
  var async: Boolean;
  var data: Any?;
  var agent: Boolean? = null;
  var isBinary: Boolean? = null;
  var supportsBinary: Boolean? = null;
  var requestTimeout: Long;
  val client = HttpClient(CIO)
  var reqMethod: String;
  var extraHeaders: Map<String, Any>?;

  constructor(opts: Map<String,Any>) {
    reqMethod = opts.getOrElse("method") { "GET" } as String;
    uri = opts["uri"] as String;
    xd = opts["xd"] == true;
    xs = opts["xs"] == true;
    async = opts["async"] != false;
    data = opts["data"] as Any?;
    agent = opts["agent"] as Boolean?;
    isBinary = opts["isBinary"] as Boolean?;
    supportsBinary = opts["supportsBinary"] as Boolean?;
    requestTimeout = opts["requestTimeout"] as Long;
    extraHeaders = opts["extraHeaders"] as Map<String, Any>?;

    create();
  }

  /**
   * Creates the XHR object and sends the request.
   *
   * @api private
  */
  fun create() {

    var self = this;

    Logger.fine("xhr open $reqMethod: $uri");
    GlobalScope.launch {
      try {
        val resp = client.request(uri) {
          when (reqMethod) {
            "GET" -> method = HttpMethod.Get
            "POST" -> method = HttpMethod.Post
            "PUT" -> method = HttpMethod.Put
          }
          headers {
            append("Accept", "*/*");
            extraHeaders?.forEach {
              append(it.key, it.value as String)
            }
            if (reqMethod == "POST") {
              if (isBinary == true) {
                append("Content-type", "application/octet-stream");
              } else {
                append("Content-type", "text/plain;charset=UTF-8");
              }
            }
          }
//          timeout { connectTimeoutMillis = requestTimeout }
          if (reqMethod == "POST") {
            setBody(data);
            println("EEE $data");
          }
        }

        if (resp.status == HttpStatusCode.OK || resp.status.value == 1223) {
          var respData: Any;
          if (resp.contentType()?.contentType == "application/octet-stream") {
            respData = resp.body<ByteArray>()
          } else {
            respData = resp.bodyAsText()
          }

          onResponseHeaders(resp.headers.toMap());

          if (respData != null) {
            if (respData is ByteArray) respData = respData.toUByteArray();
            onData(respData);
          }
        } else {
          Timer(1, fun() { onError(resp.status.value.toString()) }).schedule();
        }
      } catch (e: Exception) {
        onError("${e.message}");
        Logger.fine("XHR Error ${e.message}")
        e.printStackTrace()
      }
    }
  }

  /**
   * Called if we have data.
   *
   * @api private
  */
  fun onData(data: Any) {
    emit("data", data);
    onSuccess();
  }

  fun onResponseHeaders(data:  Map<String, List<String>>) {
    emit(EVENT_RESPONSE_HEADERS, data)
  }

  /**
   * Called upon successful response.
   *
   * @api private
  */
  fun onSuccess() {
    emit("success");
    cleanup();
  }

  /**
   * Called upon error.
   *
   * @api private
  */
  fun onError(err: String) {
    emit("error", err);
    cleanup(true);
  }

  /**
   * Cleans up house.
   *
   * @api private
  */
  fun cleanup(fromError: Boolean = false) {

//    if (fromError != null) {
//      try {
//        xhr!.abort();
//      } catch (e) {
//        // ignore
//      }
//    }
//
//    xhr = null;
  }


  /**
   * Check if it has XDomainRequest.
   *
   * @api private
  */
  fun hasXDR(): Boolean {
    // Todo: handle it in dart way
    return false;
    //  return "undefined" !== typeof global.XDomainRequest && !this.xs && this.enablesXDR;
  }

  /**
   * Aborts the request.
   *
   * @api public
  */
  fun abort() {
    cleanup();
  };
}
