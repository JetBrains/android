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
package com.android.tools.idea.editors.fast

import com.android.ide.common.gradle.Version
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.fast.FastPreviewBundle.message
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_FAST_PREVIEW_AUTO_DISABLE
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.rendering.classloading.ProjectConstantRemapper
import com.android.tools.idea.util.toDisplayString
import com.google.common.cache.CacheBuilder
import com.google.common.hash.Hashing
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.jetbrains.rd.util.getOrCreate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** Default version of the runtime to use if the dependency resolution fails when looking for the daemon. */
private val DEFAULT_RUNTIME_VERSION = Version.parse("1.1.0-alpha02")

/**
 * Converts the [Throwable] stacktrace to a string.
 */
private fun Throwable.toLogString(): String {
  val exceptionStackWriter = StringWriter()
  printStackTrace(PrintWriter(exceptionStackWriter))

  return exceptionStackWriter.toString()
}

data class DisableReason(val title: String, val description: String? = null, val throwable: Throwable? = null) {
  /**
   * True if a long description is available by calling [longDescriptionString].
   */
  val hasLongDescription: Boolean
    get() = description != null || throwable != null

  /**
   * Returns the `description` and the full `throwable` if available.
   */
  fun longDescriptionString() = (description?.let { "$it\n" } ?: "") + throwable?.toLogString()
}

/**
 * A [DisableReason] to be used when calling [FastPreviewManager.disable] if it was disabled by the user.
 */
val ManualDisabledReason = DisableReason("User disabled")

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
      }
      catch (t: Throwable) {
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
          }
          catch (t: Throwable) {
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
 * Default runtime version locator that, for a given [Module], returns the version of the runtime that
 * should be used.
 */
private fun defaultRuntimeVersionLocator(module: Module): Version =
  module.getModuleSystem()
    .getResolvedDependency(GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("+"))
    ?.lowerBoundVersion ?: DEFAULT_RUNTIME_VERSION

/**
 * Returns a [CompilerDaemonClient] that uses the in-process compiler. This is the same
 * compiler used by the Emulator Live Edit.
 */
private fun embeddedDaemonFactory(project: Project, log: Logger): CompilerDaemonClient {
  log.info("Using the experimental in-process compiler")
  return EmbeddedCompilerClientImpl(project, log)
}

/**
 * Unique ID for a given compilation requests. The ID should be the same for the same input.
 */
private typealias CompileRequestId = String

/**
 * Creates a [CompileRequestId] for the given inputs. [files] will be used to ensure the [CompileRequestId] changes if
 * one of the given files contents have changed.
 */
private fun createCompileRequestId(files: Collection<PsiFile>, module: Module): CompileRequestId {
  val filesDependency = files
    .sortedBy { it.virtualFile.path }.joinToString("\n") {
      "${it.virtualFile.path}@${it.modificationStamp}"
    }
  val compilationRequestContents = """
        $filesDependency
        ${ProjectRootModificationTracker.getInstance(module.project).modificationCount}
        """.trimIndent()

  @Suppress("UnstableApiUsage")
  return Hashing.goodFastHash(32).newHasher()
    .putString(compilationRequestContents, Charsets.UTF_8)
    .hash()
    .toString()
}

private val DEFAULT_MAX_CACHED_REQUESTS = Integer.getInteger("preview.fast.max.cached.requests", 5)

private const val FAST_PREVIEW_NOTIFICATION_GROUP_ID = "Fast Preview Notification"

/**
 * Service that talks to the compiler daemon and manages the daemons and compilation requests.
 *
 * @param project [Project] this manager is working with
 * @param alternativeDaemonFactory Optional daemon factory to use if the default one should not be used. Mainly for testing.
 * @param moduleRuntimeVersionLocator A method that given a [Module] returns the [Version] of the Compose runtime that should
 *  be used. This is useful when locating the specific kotlin compiler daemon.
 * @param maxCachedRequests Maximum number of cached requests to store by this manager. If 0, caching is disabled.
 */
@Service
class FastPreviewManager private constructor(
  private val project: Project,
  alternativeDaemonFactory: ((String, Project, Logger, CoroutineScope) -> CompilerDaemonClient)? = null,
  private val moduleRuntimeVersionLocator: (Module) -> Version = ::defaultRuntimeVersionLocator,
  maxCachedRequests: Int = DEFAULT_MAX_CACHED_REQUESTS) : Disposable {

  @Suppress("unused") // Needed for IntelliJ service constructor call
  constructor(project: Project) : this(project, null)

  private val log = Logger.getInstance(FastPreviewManager::class.java)

  private val _isDisposed = AtomicBoolean(false)
  val isDisposed: Boolean
    get() = _isDisposed.get()

  private val scope = AndroidCoroutineScope(this, workerThread)
  private val daemonFactory: ((String) -> CompilerDaemonClient) = { version ->
    alternativeDaemonFactory?.invoke(version, project, log, scope) ?: embeddedDaemonFactory(project, log)
  }
  private val daemonRegistry = DaemonRegistry(scope, daemonFactory).also {
    Disposer.register(this@FastPreviewManager, it)
  }

  /**
   * Cache that keeps the result of a given compilation. Compilation requests are disambiguated via [CompileRequestId].
   */
  private val requestTracker = CacheBuilder.newBuilder()
    .maximumSize(maxCachedRequests.toLong())
    .build<CompileRequestId, CompletableDeferred<Pair<CompilationResult, String>>>()

  private val compilingMutex = Mutex(false)

  /**
   * If true, it means that Fast Preview is disabled only for this session. If Studio is restarted, we will use the persisted configuration
   * valid in [LiveEditApplicationConfiguration].
   */
  private var disableForThisSession = false

  /**
   * Returns true when the feature is enabled
   */
  val isEnabled: Boolean
    get() = !disableForThisSession && FastPreviewConfiguration.getInstance().isEnabled

  /**
   * Returns the reason why the Fast Preview was disabled, if available.
   */
  var disableReason: DisableReason? = null
    private set

  /**
   * Returns true if the service is auto disabled and was not manually disabled by the user.
   */
  val isAutoDisabled: Boolean
    get() = !isEnabled && disableReason != null && disableReason != ManualDisabledReason

  /**
   * Allow auto disable. If set to true, the Fast Preview might disable itself automatically if there is a compiler failure.
   * This can happen if the project has unsupported features like annotation providers.
   */
  var allowAutoDisable: Boolean = COMPOSE_FAST_PREVIEW_AUTO_DISABLE.get()

  /**
   * Returns true when the feature is available. The feature will not be available if Studio is in power save mode, it's currently building
   * or fast preview is disabled.
   */
  val isAvailable: Boolean
    get() = isEnabled && !PreviewPowerSaveManager.isInPowerSaveMode

  /**
   * Returns true while there is a compilation request running of this project.
   */
  val isCompiling: Boolean
    get() = isEnabled && compilingMutex.isLocked

  /**
   * Stops all the daemons managed by this [FastPreviewManager].
   */
  fun stopAllDaemons() = daemonRegistry.stopAllDaemons()

  /**
   * Starts the appropriate daemon for the current [Module] dependencies. If this method is not called beforehand,
   * [compileRequest] will start the daemon on the first request.
   */
  fun preStartDaemon(module: Module) = scope.launch {
    try {
      daemonRegistry.getOrCreateDaemon(moduleRuntimeVersionLocator(module).toString())
    } catch(t: Throwable) {
      FastPreviewTrackerManager.getInstance(project).daemonStartFailed()
      throw t
    }
  }

  /**
   * Sends a compilation request for the given [files]s with the given context [Module] and returns if it was
   * successful and the path where the result classes can be found.
   *
   * The method takes an optional [ProgressIndicator] to update the progress of the request.
   *
   * If the compilation request is not successful and [allowAutoDisable] is true, the [FastPreviewManager] will disable
   * itself until [enable] is called again. This is to prevent code that can not be compiled using this service being
   * retried over and over. The user will have the option to re-enable it via a notification.
   *
   * The given [FastPreviewTrackerManager.Request] is used to track the metrics of this request.
   */
  @Suppress("BlockingMethodInNonBlockingContext") // Runs in the IO context
  suspend fun compileRequest(files: Collection<PsiFile>,
                             module: Module,
                             indicator: ProgressIndicator = EmptyProgressIndicator(),
                             tracker: FastPreviewTrackerManager.Request = FastPreviewTrackerManager.getInstance(project).trackRequest()): Pair<CompilationResult, String> = compilingMutex.withLock {
    val startTime = System.currentTimeMillis()
    val requestId = createCompileRequestId(files, module)
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
      return@withLock pendingRequest.await()
    }

    val outputDir = Files.createTempDirectory("overlay")
    log.debug("Compiling $outputDir (id=$requestId)")
    indicator.text = "Looking for compiler daemon"
    val runtimeVersion = moduleRuntimeVersionLocator(module).toString()

    val result = try {
      val daemon = daemonRegistry.getOrCreateDaemon(runtimeVersion)

      try {
        project.messageBus.syncPublisher(FAST_PREVIEW_MANAGER_TOPIC).onCompilationStarted(files)
      }
      catch (_: Throwable) {
      }
      indicator.text = "Compiling"
      try {
        daemon.compileRequest(files, module, outputDir, indicator)
      }
      catch (t: CancellationException) {
        CompilationResult.CompilationAborted(t)
      }
      catch (t: ProcessCanceledException) {
        CompilationResult.CompilationAborted(t)
      }
      catch (t: Throwable) {
        // Catch for compilation failures
        CompilationResult.RequestException(t)
      }
    }
    catch (t: CancellationException) {
      CompilationResult.CompilationAborted(t)
    }
    catch (t: ProcessCanceledException) {
      CompilationResult.CompilationAborted(t)
    }
    catch (t: Throwable) {
      tracker.daemonStartFailed()
      // Catch for daemon start general failures
      CompilationResult.DaemonStartFailure(t)
    }
    val durationMs = System.currentTimeMillis() - startTime
    val durationString = Duration.ofMillis(durationMs).toDisplayString()
    log.info("Compiled in $durationString (result=$result, id=$requestId)")
    if (result.isError && allowAutoDisable) {
      val reason = when (result) {
        // Handle RequestException but do not disable the compilation if it's because of a syntax error. This might be caused by the
        // user still typing.
        is CompilationResult.RequestException ->
          DisableReason(title = message("fast.preview.disabled.reason.unable.compile"),
                        description = result.e?.message,
                        throwable = result.e)
        is CompilationResult.DaemonStartFailure -> DisableReason(title = message("fast.preview.disabled.reason.unable.start"),
                                                                 throwable = result.e)
        is CompilationResult.DaemonError -> DisableReason(
          title = message("fast.preview.disabled.reason.unable.compile.compiler.error.title"),
          description = message("fast.preview.disabled.reason.unable.compile.compiler.error.description"))
        is CompilationResult.CompilationAborted, is CompilationResult.CompilationError -> null
        is CompilationResult.Success -> throw IllegalStateException("Result is not an error, no disable reason")
      }
      if (reason != null) disable(reason)
    }

    // Notify any error/success into the event log
    if (result !is CompilationResult.CompilationAborted) {
      val buildMessage = if (result.isSuccess)
        message("event.log.fast.preview.build.successful", durationString)
      else
        message("event.log.fast.preview.build.failed", durationString)
      Notification(FAST_PREVIEW_NOTIFICATION_GROUP_ID,
                   buildMessage,
                   if (result.isSuccess) NotificationType.INFORMATION else NotificationType.WARNING)
        .notify(project)
    }

    if (result.isSuccess) {
      // The project has built successfully so we can drop the constants that we were keeping.
      ProjectConstantRemapper.getInstance(project).clearConstants(null)
    }

    return@withLock Pair(result, outputDir.toAbsolutePath().toString()).also {
      synchronized(requestTracker) {
        if (result !is CompilationResult.Success && result !is CompilationResult.CompilationError) {
          // Only cache user induced results. Avoid caching of internal errors or aborted compilations.
          requestTracker.invalidate(requestId)
        }
        pendingRequest.complete(it)
    }
      try {
        project.messageBus.syncPublisher(FAST_PREVIEW_MANAGER_TOPIC).onCompilationComplete(result, files)
        if (result == CompilationResult.Success) {
          tracker.compilationSucceeded(durationMs, files.size)
        }
        else {
          tracker.compilationFailed(durationMs, files.size)
        }
      }
      catch (_: Throwable) {
      }
    }
  }

  /**
   * Sends a compilation request for the a single [file]. See [FastPreviewManager.compileRequest].
   */
  @Suppress("BlockingMethodInNonBlockingContext") // Runs in the IO context
  suspend fun compileRequest(file: PsiFile,
                             module: Module,
                             indicator: ProgressIndicator = EmptyProgressIndicator(),
                             tracker: FastPreviewTrackerManager.Request = FastPreviewTrackerManager.getInstance(project).trackRequest()): Pair<CompilationResult, String> =
    compileRequest(listOf(file), module, indicator, tracker)

  /**
   * Adds a [FastPreviewManagerListener] that will be notified when this manager has completed a build.
   */
  fun addListener(parentDisposable: Disposable, listener: FastPreviewManagerListener) {
    project.messageBus.connect(parentDisposable).subscribe(FAST_PREVIEW_MANAGER_TOPIC, listener)
  }

  /**
   * Disables the Fast Preview. Optionally, receive a reason to be disabled that might be displayed to the user.
   */
  fun disable(reason: DisableReason) {
    val wasEnabled = isEnabled
    val newReason = disableReason != reason
    disableReason = reason

    if (newReason && reason != ManualDisabledReason && reason.hasLongDescription) {
      // Log long description to the event log.
      Notification(FAST_PREVIEW_NOTIFICATION_GROUP_ID,
                   message("fast.preview.disabled.reason.unable.compile.compiler.error.description"),
                   reason.longDescriptionString(),
                   NotificationType.WARNING)
        .notify(project)
      disableForThisSession = true
    }
    else FastPreviewConfiguration.getInstance().isEnabled = false

    val tracker = FastPreviewTrackerManager.getInstance(project)
    if (reason == ManualDisabledReason)
      tracker.userDisabled()
    else
      tracker.autoDisabled()

    if (wasEnabled) {
      project.messageBus.syncPublisher(FAST_PREVIEW_MANAGER_TOPIC).onFastPreviewStatusChanged(isEnabled)
    }
  }

  /** Enables the Fast Preview. */
  fun enable() {
    val wasEnabled = isEnabled
    disableReason = null
    disableForThisSession = false
    FastPreviewTrackerManager.getInstance(project).userEnabled()
    FastPreviewConfiguration.getInstance().isEnabled = StudioFlags.COMPOSE_FAST_PREVIEW.get()

    if (!wasEnabled && isEnabled) {
      project.messageBus.syncPublisher(FAST_PREVIEW_MANAGER_TOPIC).onFastPreviewStatusChanged(isEnabled)
    }
  }

  override fun dispose() {
    _isDisposed.set(true)
  }

  @TestOnly
  fun invalidateRequestsCache() {
    synchronized(requestTracker) {
      requestTracker.invalidateAll()
    }
  }

  companion object {
    fun getInstance(project: Project): FastPreviewManager = project.getService(FastPreviewManager::class.java)

    @TestOnly
    fun getTestInstance(project: Project,
                        daemonFactory: (String, Project, Logger, CoroutineScope) -> CompilerDaemonClient,
                        moduleRuntimeVersionLocator: (Module) -> Version = ::defaultRuntimeVersionLocator,
                        maxCachedRequests: Int = DEFAULT_MAX_CACHED_REQUESTS): FastPreviewManager =
      FastPreviewManager(project = project,
                         alternativeDaemonFactory = daemonFactory,
                         moduleRuntimeVersionLocator = moduleRuntimeVersionLocator,
                         maxCachedRequests = maxCachedRequests)

    interface FastPreviewManagerListener {
      fun onCompilationStarted(files: Collection<PsiFile>)
      fun onCompilationComplete(result: CompilationResult, files: Collection<PsiFile>)
      fun onFastPreviewStatusChanged(isFastPreviewEnabled: Boolean) {}
    }

    private val FAST_PREVIEW_MANAGER_TOPIC = Topic("Fast Preview Manager Topic", FastPreviewManagerListener::class.java)
  }
}

val Project.fastPreviewManager: FastPreviewManager
  get() = FastPreviewManager.getInstance(this)