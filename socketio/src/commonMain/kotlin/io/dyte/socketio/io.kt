package io.dyte.socketio

import Manager
import ManagerOptions
import SocketClient
import io.ktor.http.*

class IO {
    companion object {
        val cache: MutableMap<String, Any> = mutableMapOf();
        fun socket(uri: String, opts: IOOptions): SocketClient {
            return _lookup(uri, opts);
        }

        fun _lookup(uri: String, opts: IOOptions): SocketClient {

            var parsed = URLBuilder(uri);
            var id = "${parsed.protocol}://${parsed.host}:${parsed.port}";
            var path = parsed.encodedPath;
            var sameNamespace =
                cache.containsKey(id) && (cache.get("id") as Manager).nsps.containsKey(path);
            var newConnection = opts.forceNew == true || opts.multiplex == false || sameNamespace;

            var io: Manager;

            if (newConnection) {
                // Logger.fine('ignoring socket cache for $uri');
                io = Manager(uri, opts);
            } else {
                io = cache.getOrElse(id) { Manager(uri, opts) } as Manager;
            }
            if (!parsed.parameters.isEmpty()) {
                opts.query = parsed.parameters.build();
            }
            return io.socket(if (parsed.pathSegments.isEmpty()) "/" else parsed.encodedPath);
        }
    }
}

class IOOptions: ManagerOptions() {
    var multiplex: Boolean? = null;
    var forceNew: Boolean? = null;
}
