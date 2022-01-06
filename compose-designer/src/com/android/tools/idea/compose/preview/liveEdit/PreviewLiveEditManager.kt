/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.liveEdit

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.StudioPathManager
import com.google.common.cache.CacheBuilder
import com.google.common.hash.Hashing
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.rd.util.getOrCreate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.android.uipreview.getLibraryDependenciesJars
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Duration
import java.util.UUID

/** Command received from the daemon to indicate the result is available. */
private const val CMD_RESULT = "RESULT"

/** Command sent to the daemon to indicate the request is complete. */
private const val CMD_DONE = "done"
private const val SUCCESS_RESULT_CODE = 0

/** Default version of the runtime to use if the dependency resolution fails when looking for the daemon. */
private val DEFAULT_RUNTIME_VERSION = GradleVersion.parse("1.1.0-alpha02")

/** Settings passed to the compiler daemon in debug mode. */
private const val DAEMON_DEBUG_SETTINGS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"

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
      if (StudioFlags.COMPOSE_LIVE_EDIT_DEBUG_DAEMON.get()) DAEMON_DEBUG_SETTINGS else null,
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
 *  - The daemon will wait for the compiler parameters that will be passed verbatim ot the kolinc compiler. The daemon will take parameters
 *  , one per line, until the string "done" is sent in a separate line.
 *  - The daemon will then send all the compiler output back to Studio via stdout. Once the compilation is done the daemon will print
 *  "RESULT <exit_code>" to stout and will start waiting for a new command line.
 *
 * @param scope the [CoroutineScope] to be used by the coroutines in the daemon.
 * @param log [Logger] used to log the debug output of the daemon.
 */
@Suppress("BlockingMethodInNonBlockingContext") // All calls are running within the IO context
private class CompilerDaemonClientImpl(daemonPath: String,
                                       private val scope: CoroutineScope,
                                       private val log: Logger) : CompilerDaemonClient {
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

  override fun dispose() {
    channel.close()
    process.destroyForcibly()
  }

  override val isRunning: Boolean
    get() = !channel.isClosedForSend && process.isAlive

  override suspend fun compileRequest(args: List<String>): CompilationResult = withContext(scope.coroutineContext) {
    val result = CompletableDeferred<CompilationResult>()
    channel.send(Request(args) { result.complete(it) })
    result.await()
  }
}

/**
 * Class responsible to managing the existing daemons and avoid multiple daemons for the same version being started.
 * The daemons are indexed based on the runtime version passed when calling [getOrCreateDaemon].
 *
 * @param scope [CoroutineScope] used for the suspend functions in this class.
 * @param daemonFactory the factory that creates a [CompilerDaemonClient] for a given version.
 */
private class DaemonRegistry(
  private val scope: CoroutineScope,
  private val daemonFactory: (String) -> CompilerDaemonClient) : Disposable {

  private val daemons: MutableMap<String, CompilerDaemonClient> = mutableMapOf()
  private val startingDaemons: MutableMap<String, CompletableDeferred<CompilerDaemonClient>> = mutableMapOf()

  /**
   * Creates a daemon in the background and waits for it to be available.
   */
  private suspend fun createDaemon(version: String): CompilerDaemonClient {
    val pendingDaemon = CompletableDeferred<CompilerDaemonClient>()
    // daemonFactory is code that might block from the caller, use a different thread for waiting.
    AppExecutorUtil.getAppExecutorService().execute {
      try {
        pendingDaemon.complete(daemonFactory(version))
      } catch (t: Throwable) {
        pendingDaemon.completeExceptionally(t)
      }
    }
    val newDaemon = withTimeout(Duration.ofSeconds(10)) {
      pendingDaemon.await()
    }
    Disposer.register(this@DaemonRegistry, newDaemon)
    return newDaemon
  }

  /**
   * Creates a new daemon for the given [version] of the Compose runtime or, returns an existing one if available
   * for that version.
   */
  suspend fun getOrCreateDaemon(version: String): CompilerDaemonClient = withContext(scope.coroutineContext) {
    synchronized(daemons) {
      val existingDaemon = daemons[version]
      if (existingDaemon?.isRunning == true) return@withContext existingDaemon
      // Ensure it's removed from the current list in case it had stopped running.
      daemons.remove(version)

      // We did not have an existing one so start a request. startingDaemons avoids duplicating requests.
      return@synchronized startingDaemons.getOrCreate(version) {
        val pending = CompletableDeferred<CompilerDaemonClient>()
        // Launch a new coroutine for the daemon creation, so we do not block in the synchronized block.
        // This coroutine will do the potentially heavy daemon creation and complete the pending CompletableDeferred once
        // it's done.
        scope.launch {
          try {
            val newDaemon = createDaemon(version)
            synchronized(daemons) {
              if (startingDaemons.remove(version) != null) {
                daemons[version] = newDaemon
              }
            }
            pending.complete(newDaemon)
          } catch (t: Throwable) {
            // Failed to instantiate the daemon, notify the failure to listeners.
            synchronized(daemons) {
              startingDaemons.remove(version)
            }
            pending.completeExceptionally(t)
          }

        }
        pending
      }
    }.await()
  }

  /**
   * Stops all the daemons registered in this registry. The operation is executed asynchronously.
   */
  fun stopAllDaemons() = scope.launch {
    synchronized(daemons) {
      val allDaemons = daemons.values
      daemons.clear()
      startingDaemons.clear()
      allDaemons
    }.forEach { Disposer.dispose(it) }
  }

  override fun dispose() {
    stopAllDaemons()
  }
}

/**
 * Default class path locator that returns the complete classpath to pass to the compiler for a given
 * [Module].
 */
private fun defaultCompileClassPathLocator(module: Module): List<String> {
  // Build classpath
  val mainArtifact = GradleAndroidModel.get(module)
    ?.selectedVariant
    ?.mainArtifact
  val modulePath = listOfNotNull(
    CompilerModuleExtension.getInstance(module)?.compilerOutputPath?.path
  ) + (mainArtifact?.classesFolder?.map { it.absolutePath } ?: emptyList())

  val libraryDeps = module.getLibraryDependenciesJars()
    .map { it.toString() }

  val bootclassPath = AndroidPlatform.getInstance(module)
                        ?.target
                        ?.bootClasspath ?: listOf()
  // The Compose plugin is included as part of the fat daemon jar so no need to specify it

  return (libraryDeps + modulePath + bootclassPath)
}

/**
 * Default runtime version locator that, for a given [Module], returns the version of the runtime that
 * should be used.
 */
private fun defaultRuntimeVersionLocator(module: Module): GradleVersion =
  module.getModuleSystem()
    .getResolvedDependency(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("+"))
    ?.version ?: DEFAULT_RUNTIME_VERSION

/**
 * Finds the `kotlin-compiler-daemon` jar for the given [version] that is included as part of the plugin.
 * This method does not check the path actually exists.
 */
private fun findDaemonPath(version: String): String {
  val homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath())
  val jarRootPath = if (StudioPathManager.isRunningFromSources()) {
    FileUtil.join(StudioPathManager.getSourcesRoot(), "tools/adt/idea/compose-designer/lib/")
  }
  else {
    // When running as part of the distribution, we allow also to override the path to the daemon via a system property.
    System.getProperty("preview.live.edit.daemon.path", FileUtil.join(homePath, "plugins/design-tools/resources/"))
  }

  return FileUtil.join(jarRootPath, "kotlin-compiler-daemon-$version.jar")
}

/**
 * Default daemon factory to be used in production. This factory will instantiate [CompilerDaemonClientImpl] for the
 * given version.
 * This factory will try to find a daemon for the given specific version or fallback to a stable one if the specific one
 * is not found. For example, for `1.1.0-alpha02`, this factory will try to locate the jar for the daemon
 * `kotlin-compiler-daemon-1.1.0-alpha02.jar`. If not found, it will alternatively try `kotlin-compiler-daemon-1.1.0.jar` since
 * it should be compatible.
 */
private fun defaultDaemonFactory(version: String, log: Logger, scope: CoroutineScope): CompilerDaemonClient {
  // Prepare fallback versions
  val daemonPath = linkedSetOf(
    version,
    "${version.substringBeforeLast("-")}-fallback", // Find the fallback artifact for this same version
    "${version.substringBefore(".")}.0.0-fallback"  // Find the fallback artifact for the same major version
  ).asSequence().map {
    log.debug("Looking for kotlin daemon version '${version}'")
    findDaemonPath(it)
  }.find { FileUtil.exists(it) } ?: throw FileNotFoundException("Unable to find kotlin daemon for version '$version'")
  log.info("Starting daemon $daemonPath")
  return CompilerDaemonClientImpl(daemonPath, scope, log)
}

/**
 * Unique ID for a given compilation requests. The ID should be the same for the same input.
 */
private typealias CompileRequestId = String

/**
 * Creates a [CompileRequestId] for the given inputs. [files] will be used to ensure the [CompileRequestId] changes if
 * one of the given files contents have changed. [requestUniqueArgs] is the list of unique arguments for this requests, usually
 * the classpath of the request.
 */
private fun createCompileRequestId(files: Collection<PsiFile>, requestUniqueArgs: List<String>): CompileRequestId {
  val filesDependency = files
    .sortedBy { it.virtualFile.path }.joinToString("\n") {
      "${it.virtualFile.path}@${it.modificationStamp}"
    }

  val compilationRequestContents = """
        $filesDependency
        ${requestUniqueArgs.joinToString(" ")}
        """.trimIndent()

  @Suppress("UnstableApiUsage")
  return Hashing.goodFastHash(32).newHasher()
    .putString(compilationRequestContents, Charsets.UTF_8)
    .hash()
    .toString()
}

private val FIXED_COMPILER_ARGS = listOf(
  "-verbose",
  "-version",
  "-no-stdlib", "-no-reflect", // Included as part of the libraries classpath
  "-Xdisable-default-scripting-plugin",
  "-jvm-target", "1.8")
private val DEFAULT_MAX_CACHED_REQUESTS = Integer.getInteger("preview.live.edit.max.cached.requests", 5)

/**
 * Service that talks to the compiler daemon and manages the daemons and compilation requests.
 *
 * @param alternativeDaemonFactory Optional daemon factory to use if the default one should not be used. Mainly for testing.
 * @param moduleClassPathLocator A method that given a [Module] returns the classpath to be passed to the compiler when making
 *  compilation requests for it.
 * @param moduleRuntimeVersionLocator A method that given a [Module] returns the [GradleVersion] of the Compose runtime that should
 *  be used. This is useful when locating the specific kotlin compiler daemon.
 * @param maxCachedRequests Maximum number of cached requests to store by this manager. If 0, caching is disabled.
 */
@Service
class PreviewLiveEditManager private constructor(
  alternativeDaemonFactory: ((String) -> CompilerDaemonClient)? = null,
  private val moduleClassPathLocator: (Module) -> List<String> = ::defaultCompileClassPathLocator,
  private val moduleRuntimeVersionLocator: (Module) -> GradleVersion = ::defaultRuntimeVersionLocator,
  maxCachedRequests: Int = DEFAULT_MAX_CACHED_REQUESTS) : Disposable {

  @Suppress("unused") // Needed for IntelliJ service constructor call
  constructor(project: Project) : this(null)

  private val log = Logger.getInstance(PreviewLiveEditManager::class.java)

  private val scope = AndroidCoroutineScope(this, ioThread)
  private val daemonFactory = alternativeDaemonFactory ?: {
    defaultDaemonFactory(it, log, scope)
  }
  private val daemonRegistry = DaemonRegistry(scope, daemonFactory).also {
    Disposer.register(this@PreviewLiveEditManager, it)
  }

  /**
   * Cache that keeps the result of a given compilation. Compilation requests are disambiguated via [CompileRequestId].
   */
  private val requestTracker = CacheBuilder.newBuilder()
    .maximumSize(maxCachedRequests.toLong())
    .build<CompileRequestId, CompletableDeferred<Pair<CompilationResult, String>>>()

  /**
   * Stops all the daemons managed by this [PreviewLiveEditManager].
   */
  fun stopAllDaemons() = daemonRegistry.stopAllDaemons()

  /**
   * Starts the appropriate daemon for the current [Module] dependencies. If this method is not called beforehand,
   * [compileRequest] will start the daemon on the first request.
   */
  fun preStartDaemon(module: Module) = scope.launch {
    daemonRegistry.getOrCreateDaemon(moduleRuntimeVersionLocator(module).toString())
  }

  /**
   * Sends a compilation request for the given [files]s with the given context [Module] and returns if it was
   * successful and the path where the result classes can be found.
   *
   * The method takes an optional [ProgressIndicator] to update the progress of the request.
   */
  @Suppress("BlockingMethodInNonBlockingContext") // Runs in the IO context
  suspend fun compileRequest(files: Collection<PsiFile>,
                             module: Module,
                             indicator: ProgressIndicator = EmptyProgressIndicator()): Pair<CompilationResult, String> =
    withContext(scope.coroutineContext) {
      val startTime = System.currentTimeMillis()
      indicator.text = "Building classpath"
      val classPathString = moduleClassPathLocator(module).joinToString(File.pathSeparator)
      val classPathArgs = if (classPathString.isNotBlank()) listOf("-cp", classPathString) else emptyList()

      val requestId = createCompileRequestId(files, classPathArgs)
      val (isRunning: Boolean, pendingRequest: CompletableDeferred<Pair<CompilationResult, String>>) = synchronized(requestTracker) {
        var isRunning = true
        val request = requestTracker.get(requestId) {
          log.debug("New request with id=$requestId")
          isRunning = false
          CompletableDeferred()
        }
        isRunning to request
      }
      // If the request is already running, we wait for the result of that one instead.
      if (isRunning) {
        log.debug("Waiting for request id=$requestId")
        return@withContext pendingRequest.await()
      }

      val outputDir = Files.createTempDirectory("overlay")

      log.debug("output $outputDir (id=$requestId)")
      val outputAbsolutePath = outputDir.toAbsolutePath().toString()
      log.debug("Compiling $outputAbsolutePath (id=$requestId)")

      val inputFilesArgs = files.map { it.virtualFile.path }.toList()
      val args = FIXED_COMPILER_ARGS +
                 classPathArgs +
                 listOf("-d", outputAbsolutePath) +
                 inputFilesArgs

      indicator.text = "Looking for compiler daemon"
      val runtimeVersion = moduleRuntimeVersionLocator(module).toString()
      val daemon = daemonRegistry.getOrCreateDaemon(runtimeVersion)

      indicator.text = "Compiling"
      val result = daemon.compileRequest(args)
      log.info("Compiled in ${System.currentTimeMillis() - startTime}ms (result=$result, id=$requestId)")
      Pair(result, outputAbsolutePath).also {
        synchronized(requestTracker) {
          pendingRequest.complete(it)
        }
      }
    }

  /**
   * Sends a compilation request for the a single [file]. See [PreviewLiveEditManager.compileRequest].
   */
  @Suppress("BlockingMethodInNonBlockingContext") // Runs in the IO context
  suspend fun compileRequest(file: PsiFile,
                             module: Module,
                             indicator: ProgressIndicator = EmptyProgressIndicator()): Pair<CompilationResult, String> =
    compileRequest(listOf(file), module, indicator)

  override fun dispose() {}

  companion object {
    fun getInstance(project: Project): PreviewLiveEditManager = project.getService(PreviewLiveEditManager::class.java)

    @TestOnly
    fun getTestInstance(project: Project,
                        daemonFactory: (String) -> CompilerDaemonClient,
                        moduleClassPathLocator: (Module) -> List<String> = ::defaultCompileClassPathLocator,
                        moduleRuntimeVersionLocator: (Module) -> GradleVersion = ::defaultRuntimeVersionLocator,
                        maxCachedRequests: Int = DEFAULT_MAX_CACHED_REQUESTS): PreviewLiveEditManager =
      PreviewLiveEditManager(alternativeDaemonFactory = daemonFactory,
                             moduleClassPathLocator = moduleClassPathLocator,
                             moduleRuntimeVersionLocator = moduleRuntimeVersionLocator,
                             maxCachedRequests = maxCachedRequests)
  }
}