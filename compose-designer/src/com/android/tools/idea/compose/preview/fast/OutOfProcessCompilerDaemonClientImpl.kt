/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.UUID


/** Command received from the daemon to indicate the result is available. */
private const val CMD_RESULT = "RESULT"

/** Command sent to the daemon to indicate the request is complete. */
private const val CMD_DONE = "done"
private const val SUCCESS_RESULT_CODE = 0

/** Settings passed to the compiler daemon in debug mode. */
private const val DAEMON_DEBUG_SETTINGS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"

private val FIXED_COMPILER_ARGS = listOf(
  "-verbose",
  "-version",
  "-no-stdlib", "-no-reflect", // Included as part of the libraries classpath
  "-Xdisable-default-scripting-plugin",
  "-jvm-target", "1.8")

/**
 * Arguments to pass to the compiler when we want Live Literals code generation to be enabled.
 */
private val LIVE_LITERALS_ARGS = listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:liveLiterals=true")

/**
 * Starts the daemon in the given [daemonPath].
 */
private fun startDaemon(daemonPath: String): Process {
  val javaCommand = IdeSdks.getInstance().jdk
                      ?.homePath
                      ?.let { javaHomePath -> "$javaHomePath/bin/java" }
                    ?: throw IllegalStateException("No SDK found")
  return ProcessBuilder().command(
    listOfNotNull(
      javaCommand,
      // This flag can be used to start the daemon in debug mode and debug issues on the daemon JVM.
      if (StudioFlags.COMPOSE_FAST_PREVIEW_DAEMON_DEBUG.get()) DAEMON_DEBUG_SETTINGS else null,
      "-jar",
      daemonPath
    )
  ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
}

/**
 * Implementation of the [CompilerDaemonClient] that talks to a kotlin daemon in a separate JVM. The daemon is built as part
 * of the androidx tree and passed as `daemonPath` to this class constructor.
 *
 * This implementation starts the daemon in a separate JVM and uses stdout to communicate. The daemon will wait for input before
 * starting a compilation.
 *
 * The protocol is as follows:
 *  - The daemon will wait for the compiler parameters that will be passed verbatim ot the kolinc compiler. The daemon will take
 *  parameters, one per line, until the string "done" is sent in a separate line.
 *  - The daemon will then send all the compiler output back to Studio via stdout. Once the compilation is done the daemon will print
 *  "RESULT <exit_code>" to stout and will start waiting for a new command line.
 *
 * @param scope the [CoroutineScope] to be used by the coroutines in the daemon.
 * @param log [Logger] used to log the debug output of the daemon.
 */
@Suppress("BlockingMethodInNonBlockingContext") // All calls are running within the IO context
internal class OutOfProcessCompilerDaemonClientImpl(daemonPath: String,
                                                    private val scope: CoroutineScope,
                                                    private val log: Logger,
                                                    private val moduleClassPathLocator: (Module) -> List<String>,
                                                    private val moduleDependenciesClassPathLocator: (Module) -> List<String>
) : CompilerDaemonClient {
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
    scope.launch(handler) {
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
              log.debug("[${call.id}] Result $resultCode in ${System.currentTimeMillis() - requestStart}ms")

              call.onComplete(when (resultCode) {
                                SUCCESS_RESULT_CODE -> CompilationResult.Success
                                else -> CompilationResult.DaemonError(resultCode)
                              })
              break
            }
            ensureActive()
          }
          while (true)
        }
        catch (t: Throwable) {
          log.error(t)
          call.onComplete(CompilationResult.RequestException(t))
        }
        ensureActive()
      }
    }.apply { start() }
  }

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module, outputDirectory: Path, indicator: ProgressIndicator): CompilationResult {
    indicator.text = "Building classpath"
    val moduleClassPath = moduleClassPathLocator(module)
    val moduleDependenciesClassPath = moduleDependenciesClassPathLocator(module)
    val classPathString = (moduleClassPath + moduleDependenciesClassPath).joinToString(File.pathSeparator)
    val classPathArgs = if (classPathString.isNotBlank()) listOf("-cp", classPathString) else emptyList()

    val inputFilesArgs = files.map { it.virtualFile.path }.toList()
    val liveLiteralsArgs = if (LiveEditApplicationConfiguration.getInstance().isLiveLiterals)
      LIVE_LITERALS_ARGS
    else emptyList()
    val friendPaths = listOf(
      "-Xfriend-paths=${moduleClassPath.joinToString(",")}"
    )
    val outputAbsolutePath = outputDirectory.toAbsolutePath().toString()
    val args = FIXED_COMPILER_ARGS +
               liveLiteralsArgs +
               classPathArgs +
               friendPaths +
               listOf("-d", outputAbsolutePath) +
               inputFilesArgs
    return compile(args)
  }

  override fun dispose() {
    channel.close()
    process.destroyForcibly()
  }

  override val isRunning: Boolean
    get() = !channel.isClosedForSend && process.isAlive

  private suspend fun compile(args: List<String>): CompilationResult = withContext(scope.coroutineContext) {
    val result = CompletableDeferred<CompilationResult>()
    channel.send(Request(args) { result.complete(it) })
    result.await()
  }
}