package io.dyte.socketio.src.engine

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

class Timer(timeMillis: Long, Fx: () -> Unit) :
  CoroutineScope { // implement CoroutineScope to create local scope
  private var job: Job = Job()
  private var timeMillis = timeMillis
  private var Fx = Fx
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + job

  // this method will help to stop execution of a coroutine.
  // Call it to cancel coroutine and to break the while loop defined in the coroutine below
  fun cancel() {
    job.cancel()
  }

  fun schedule() = launch { // launching the coroutine
    delay(timeMillis)
    Fx()
  }
}
