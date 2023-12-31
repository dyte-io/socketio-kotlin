package io.dyte.socketio.engine

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Timer(private var timeMillis: Long, private var tick: () -> Unit) :
  CoroutineScope { // implement CoroutineScope to create local scope
  private var job: Job = Job()
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default + job

  // this method will help to stop execution of a coroutine.
  // Call it to cancel coroutine and to break the while loop defined in the coroutine below
  fun cancel() {
    job.cancel()
  }

  fun schedule() = launch { // launching the coroutine
    delay(timeMillis)
    tick()
  }
}
