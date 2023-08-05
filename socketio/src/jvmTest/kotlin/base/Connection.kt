package base

import EngingeSocketOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class Connection(val serverType: String) {
  private var serverProcess: Process? = null
  private var serverService: ExecutorService? = null
  private var serverOutout: Future<*>? = null
  private var serverError: Future<*>? = null

  @BeforeTest
  @Throws(java.io.IOException::class, java.lang.InterruptedException::class)
  fun startServer() {
    println("Starting server ...")
    val latch = CountDownLatch(1)
    serverProcess =
      Runtime.getRuntime().exec("node src/jvmTest/resources/${serverType}/server.js", createEnv())
    serverService = Executors.newCachedThreadPool()

    serverOutout =
      serverService!!.submit(
        object : Runnable {
          override fun run() {
            val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
            var line: String
            try {
              line = reader.readLine()
              latch.countDown()
              do {
                println("SERVER OUT: $line")
              } while (reader.readLine().also { line = it } != null)
            } catch (e: IOException) {
              println(e.message)
            }
          }
        }
      )
    serverError =
      serverService!!.submit(
        object : Runnable {
          override fun run() {
            val reader = BufferedReader(InputStreamReader(serverProcess!!.errorStream))
            var line: String
            try {
              while (reader.readLine().also { line = it } != null) {
                logger.fine("SERVER ERR: $line")
              }
            } catch (e: IOException) {
              logger.warning(e.message)
            }
          }
        }
      )

    latch.await(3000, TimeUnit.MILLISECONDS)
  }

  @AfterTest
  @Throws(java.lang.InterruptedException::class)
  fun stopServer() {
    logger.fine("Stopping server ...")
    serverProcess?.destroy()
    serverOutout?.cancel(false)
    serverError?.cancel(false)
    serverService?.shutdown()
    serverService?.awaitTermination(3000, TimeUnit.MILLISECONDS)
  }

  fun createOptions(): EngingeSocketOptions {
    val opts = EngingeSocketOptions()
    opts.port = PORT
    return opts
  }

  fun createEnv(): Array<String?> {
    val env: MutableMap<String, String> = java.util.HashMap<String, String>(System.getenv())
    env["DEBUG"] = "engine*"
    env["PORT"] = PORT.toString()
    val _env = arrayOfNulls<String>(env.size)
    var i = 0
    for (key in env.keys) {
      _env[i] = key + "=" + env[key]
      i++
    }
    return _env
  }

  companion object {
    private val logger: Logger = Logger.getGlobal()
    const val TIMEOUT = 6000
    const val PORT = 3000
  }
}
