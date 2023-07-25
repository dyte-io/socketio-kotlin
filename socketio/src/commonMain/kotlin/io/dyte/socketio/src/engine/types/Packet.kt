package io.dyte.socketio.src.engine.types

class Packet<T> {
    var type = -1
    var id = -1
    var nsp: String? = null
    var data: T? = null
    var attachments = 0

    constructor() {}
    constructor(type: Int) {
        this.type = type
    }

    constructor(type: Int, data: T) {
        this.type = type
        this.data = data
    }
}