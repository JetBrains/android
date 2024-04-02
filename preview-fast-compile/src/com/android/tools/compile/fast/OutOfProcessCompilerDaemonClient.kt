/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.compile.fast

import com.android.tools.environment.Logger
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Command received from the daemon to indicate the result is available. */
private const val CMD_RESULT = "RESULT"

/** Command sent to the daemon to indicate the request is complete. */
private const val CMD_DONE = "done"
private const val SUCCESS_RESULT_CODE = 0

/** Settings passed to the compiler daemon in debug mode. */
private const val DAEMON_DEBUG_SETTINGS =
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"

private val FIXED_COMPILER_ARGS =
  listOf(
    "-verbose",
    "-version",
    "-no-stdlib",
    "-no-reflect", // Included as part of the libraries classpath
    "-Xdisable-default-scripting-plugin",
    "-jvm-target",
    "1.8",
  )

/**
 * Kotlin compiler daemon that can be used outside of Android studio for compiling Kotlin source
 * file on the fly. Can be used for the fast compilation (and afterwards execution) of a small
 * portion of changing source code in big project.
 *
 * @param version the version of the kotlin compiler daemon
 * @param scope the [CoroutineScope] to be used by the coroutines in the daemon client
 * @param javaCommand the path to the java executable (e.g. /usr/bin/java)
 * @param findDaemonPath constructor of the path to the daemon jar accepting daemon version
 * @param log logger
 * @param isDebug flag to pass additional command line parameters to make daemon debuggable
 */
class OutOfProcessCompilerDaemonClient(
  version: String,
  private val scope: CoroutineScope,
  private val javaCommand: String,
  private val daemonJarRootPath: Path,
  private val log: Logger,
  private val isDebug: Boolean,
) {
  private fun getDaemonPath(version: String): Path =
    // Prepare fallback versions
    linkedSetOf(
        version,
        "${version.substringBeforeLast("-")}-fallback", // Find the fallback artifact for this same
        // version
        "${version.substringBeforeLast(".")}.0-fallback", // Find the fallback artifact for the same
        // major version, e.g. 1.1
        "${version.substringBefore(".")}.0.0-fallback", // Find the fallback artifact for the same
        // major version, e.g. 1
      )
      .asSequence()
      .map { daemonJarRootPath.resolve("kotlin-compiler-daemon-$it.jar") }
      .find { it.exists() }
      ?: throw FileNotFoundException("Unable to find kotlin daemon for version '$version'")

  /** Starts the daemon in the given [daemonPath]. */
  private fun startDaemon(daemonPath: String): Process {
    log.info("Starting daemon $daemonPath")
    return ProcessBuilder()
      .command(
        listOfNotNull(
          javaCommand,
          // This flag can be used to start the daemon in debug mode and debug issues on the daemon
          // JVM.
          if (isDebug) DAEMON_DEBUG_SETTINGS else null,
          "-jar",
          daemonPath,
        )
      )
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()
  }

  private val daemonPath: String = getDaemonPath(version).absolutePathString()
  private val daemonShortId = daemonPath.substringAfterLast("/")

  data class Request(val parameters: List<String>, val onComplete: (CompilationResult) -> Unit) {
    val id = UUID.randomUUID().toString()
  }

  private val process: Process = startDaemon(daemonPath)
  private val writer = process.outputStream.bufferedWriter()
  private val reader = process.inputStream.bufferedReader()

  /** [Channel] to send the compilation request. */
  private val channel = Channel<Request>()

  init {
    val handler = CoroutineExceptionHandler { _, exception ->
      log.info("Daemon stopped ($daemonShortId)", exception)
      channel.close(exception)
    }
    scope
      .launch(handler) {
        log.info("Daemon thread started ($daemonShortId)")
        while (true) {
          val call = channel.receive()

          try {
            log.debug("[${call.id}] New request")
            val requestStart = System.currentTimeMillis()
            call.parameters.forEach {
              writer.write(it)
              writer.write("\n")
            }
            writer.write("$CMD_DONE\n")
            writer.flush()
            do {
              val line = reader.readLine() ?: break
              log.debug("[${call.id}] $line")
              if (line.startsWith(CMD_RESULT)) {
                val resultLine = line.split(" ")
                val resultCode = resultLine.getOrNull(1)?.toInt() ?: -1
                log.debug(
                  "[${call.id}] Result $resultCode in ${System.currentTimeMillis() - requestStart}ms"
                )

                call.onComplete(
                  when (resultCode) {
                    SUCCESS_RESULT_CODE -> CompilationResult.Success
                    else -> CompilationResult.DaemonError(resultCode)
                  }
                )
                break
              }
              ensureActive()
            } while (true)
          } catch (t: Throwable) {
            log.error(t)
            call.onComplete(CompilationResult.RequestException(t))
          }
          ensureActive()
        }
      }
      .apply { start() }
  }

  /**
   * Method to perform the actual compilation of the files specified by the list of paths with
   * [inputFilesArgs] using [classPath] jars/classes and with [friendPaths] (classes with visible
   * internal methods) with the result written to [outputDirectory].
   */
  suspend fun compile(
    inputFilesArgs: List<String>,
    friendPaths: List<String>,
    classPath: List<String>,
    outputDirectory: Path,
  ): CompilationResult {
    val classPathString = classPath.joinToString(File.pathSeparator)
    val classPathArgs =
      if (classPathString.isNotBlank()) listOf("-cp", classPathString) else emptyList()
    val friendPathsArgs = listOf("-Xfriend-paths=${friendPaths.joinToString(",")}")
    val outputAbsolutePath = outputDirectory.toAbsolutePath().toString()
    val args =
      FIXED_COMPILER_ARGS +
        classPathArgs +
        friendPathsArgs +
        listOf("-d", outputAbsolutePath) +
        inputFilesArgs
    return compile(args)
  }

  fun destroy() {
    channel.close()
    process.destroyForcibly()
  }

  val isRunning: Boolean
    get() = !channel.isClosedForSend && process.isAlive

  private suspend fun compile(args: List<String>): CompilationResult =
    withContext(scope.coroutineContext) {
      val result = CompletableDeferred<CompilationResult>()
      channel.send(Request(args) { result.complete(it) })
      result.await()
    }
}
