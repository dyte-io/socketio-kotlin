
class Util{
  companion object {
    fun on(obj: EventEmitter, ev: String, fn: (data: Any?) -> Unit): Destroyable {
      obj.on(ev, fn);
      return Destroyable(fun() {
        println("DESTROYING $ev")
        obj.off(ev, fn)
      });
    }
  }
}

class Destroyable {
  var callback: () -> Unit;
  constructor(cb: () -> Unit){
    this.callback = cb
  }
  fun destroy() {
    callback();
  };
}
