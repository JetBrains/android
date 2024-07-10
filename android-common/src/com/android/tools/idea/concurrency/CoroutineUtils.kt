/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.concurrency

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * [CoroutineDispatcher]s equivalent to executors defined in [AndroidExecutors].
 */
object AndroidDispatchers {
  /**
   * [CoroutineDispatcher] that dispatches to the UI thread with [ModalityState.defaultModalityState].
   *
   * @see AndroidExecutors.uiThreadExecutor
   */
  val uiThread: CoroutineDispatcher get() = uiThread(ModalityState.defaultModalityState())

  /**
   * Creates a [CoroutineDispatcher] that uses the UI thread with the given [ModalityState].
   *
   * @see AndroidExecutors.uiThreadExecutor
   */
  fun uiThread(modalityState: ModalityState): CoroutineDispatcher {
    return Executor { block -> AndroidExecutors.getInstance().uiThreadExecutor(modalityState, block) }.asCoroutineDispatcher()
  }

  /**
   * [CoroutineDispatcher] that dispatches to a background worker thread.
   *
   * @see AndroidExecutors.workerThreadExecutor
   */
  val workerThread: CoroutineDispatcher get() = AndroidExecutors.getInstance().workerThreadExecutor.asCoroutineDispatcher()

  /**
   * [CoroutineDispatcher] that dispatches to a disk IO thread. Please notice that the disk IO
   * thread pool is very limited and should not be used for anything except local disk IO.
   * For socket IO and inter-process communication please use [kotlinx.coroutines.Dispatchers.IO].
   *
   * @see AndroidExecutors.diskIoThreadExecutor
   */
  val diskIoThread: CoroutineDispatcher get() = AndroidExecutors.getInstance().diskIoThreadExecutor.asCoroutineDispatcher()
}

private val LOG: Logger get() = Logger.getInstance("CoroutinesUtils.kt")

/**
 * Exception handler similar to IDEA's default behavior (see [com.intellij.idea.StartupUtil.installExceptionHandler]) that additionally
 * logs the [CoroutineName] from context.
 */
val androidCoroutineExceptionHandler = CoroutineExceptionHandler { ctx, throwable ->
  if (throwable !is ProcessCanceledException) {
    val coroutineName = ctx[CoroutineName]?.name
    if (coroutineName != null) {
      LOG.error(coroutineName, throwable)
    }
    else {
      LOG.error(throwable)
    }
  }
}

/**
 * Creates a coroutine scope matching the [Disposable]'s lifecycle. When the [Disposable] gets
 * disposed, the scope's job is also canceled.
 *
 * The scope is created with a [SupervisorJob], meaning that a child coroutine failing will not
 * cause the other coroutines in the scope to fail.
 *
 * @param dispatcher The dispatcher to use when creating the scope. Defaults to [Dispatchers.Default].
 * @param extraContext The context to append to the scope's context. Can be used to provide
 *      a [CoroutineName], for example. Defaults to [EmptyCoroutineContext].
 */
fun Disposable.createCoroutineScope(
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  extraContext: CoroutineContext = EmptyCoroutineContext,
): CoroutineScope {
  val job = SupervisorJob()
  val scopeDisposable = Disposable { job.cancel("Disposing") }
  Disposer.register(this, scopeDisposable)
  return CoroutineScope(job + dispatcher + extraContext)
}

/**
 * Creates a [Job] tied to the lifecycle of a [Disposable].
 */
@Suppress("FunctionName") // mirroring upstream API.
fun SupervisorJob(disposable: Disposable): Job {
  return SupervisorJob().also { job ->
    cancelJobOnDispose(disposable, job)
  }
}

/**
 * Returns a [CoroutineScope] containing:
 *   - a [SupervisorJob] tied to the [Disposable] lifecycle of [disposable]
 *   - [AndroidDispatchers.workerThread]
 *   - a [CoroutineExceptionHandler] that logs unhandled exception at `ERROR` level.
 *
 * The optional [context] parameter can be used to override the [Job], [CoroutineDispatcher]
 * and [CoroutineExceptionHandler] of the [CoroutineContext] of the returned [CoroutineScope].
 *
 * Note: This method creates a "top-level" (or "root") [CoroutineScope]. Use [createChildScope]
 * to create a [CoroutineScope] to create a child scope that is tied to both a [Disposable]
 * and a parent scope.
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun AndroidCoroutineScope(disposable: Disposable, context: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
  return CoroutineScope(SupervisorJob() + workerThread + androidCoroutineExceptionHandler + context).apply {
    cancelJobOnDispose(disposable, coroutineContext.job)
  }
}

/**
 * Ensure [job] is cancelled if it is still active when [disposable] is disposed.
 */
private fun cancelJobOnDispose(disposable: Disposable, job: Job) {
  val disposableId = disposable.toString() // Don't capture the parent disposable inside the lambda.
  Disposer.register(disposable) {
    if (!job.isCancelled) {
      job.cancel(CancellationException("$disposableId has been disposed."))
    }
  }
}

/** Returns a coroutine scope that is tied to the [com.intellij.openapi.application.Application]'s lifecycle. */
fun applicationCoroutineScope(context: CoroutineContext = EmptyCoroutineContext): CoroutineScope =
  AndroidCoroutineScope(service<ApplicationCoroutineScopeDisposable>(), context)

/** Returns a coroutine scope that is tied to the [Project]'s lifecycle. */
fun Project.coroutineScope(context: CoroutineContext = EmptyCoroutineContext): CoroutineScope =
  AndroidCoroutineScope(service<ProjectDisposable>(), context)

/**
 * Returns a [Disposable] that is disposed when the [CoroutineScope] scope completes. The returned
 * [Disposable] can be used as the root. This is analogous to [AndroidCoroutineScope] where this
 * generates a [Disposable] for the given [CoroutineScope].
 */
fun CoroutineScope.scopeDisposable(): Disposable {
  val disposable = Disposer.newDisposable(service<ApplicationCoroutineScopeDisposable>())

  // The disposable should be disposed of once the coroutine scope is ended
  coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }
  return disposable
}

/**
 * This application level service is used to ensure all Disposables created by
 * [CoroutineScope.scopeDisposable] get disposed when the application is disposed.
 * [Job.invokeOnCompletion] does not provide thread guarantees (cf.
 * https://github.com/Kotlin/kotlinx.coroutines/issues/3505) and we had some race conditions where
 * the UndisposedAndroidObjectsCheckerRule#checkUndisposedAndroidRelatedObjects leak check would run
 * before the call to [Disposable.dispose] inside the [Job.invokeOnCompletion]. This created some
 * errors in some tests, for example: b/328290264. By using this application level service as a
 * parent disposable, we ensure all child disposables are disposed of at the end of each test.
 */
@Service(Service.Level.APP)
private class ApplicationCoroutineScopeDisposable : Disposable {
  override fun dispose() {}
}

@Service(Service.Level.APP)
private class ProjectDisposable : Disposable {
  override fun dispose() {}
}

/**
 * Launches a new coroutine that will be bound to the given [ProgressIndicatorEx]. If the indicator is stopped, the coroutine will be
 * cancelled. If the coroutine finishes or is cancelled, the indicator will also be stopped.
 * This method also accepts an optional [CoroutineContext].
 */
fun CoroutineScope.launchWithProgress(
  progressIndicator: ProgressIndicator,
  context: CoroutineContext = EmptyCoroutineContext,
  runnable: suspend CoroutineScope.() -> Unit): Job {
  if (!progressIndicator.isRunning) progressIndicator.start()

  // We create a new scope that we will cancel if the progressIndicator is stopped.
  val scope = createChildScope()

  /**
   * Checks if [progressIndicator] is cancelled and cancels the scope. Returns true as long as the scope is still active.
   */
  fun checkProgressIndicatorState(): Boolean {
    if (progressIndicator.isCanceled) {
      scope.cancel("User cancelled the refresh")
    }
    else if (!progressIndicator.isRunning) {
      scope.cancel("The progress indicator is not running")
    }

    return scope.isActive
  }

  scope.launch(workerThread) {
    while (checkProgressIndicatorState()) {
      delay(500)
    }
  }

  return scope.launch(context = scope.coroutineContext + context, block = runnable).apply {
    invokeOnCompletion {
      // The coroutine completed so, if needed, we stop the indicator.
      if (progressIndicator.isRunning) {
        progressIndicator.stop()
        (progressIndicator as? ProgressIndicatorEx)?.processFinish()
      }
    }
  }
}

/**
 * Mixin interface for IDE components aware of Android conventions for using Kotlin coroutines.
 *
 * To properly use coroutines, the component needs to meet these requirements:
 * - Be [Disposable]. Disposing the component cancels all coroutines created in the scope of this object.
 * - Implement [UserDataHolderEx], so the single [Job] tied to the [Disposable] lifecycle can be stored and reused. This can be done by
 *   delegating to a fresh instance of [UserDataHolderBase], e.g. `class Foo : UserDataHolder by UserDataHolderBase(), ...`
 *
 * Alternatively, an IDE component may use [AndroidCoroutineScope] to store the scope in an explicit field.
 */
interface AndroidCoroutinesAware : UserDataHolderEx, Disposable, CoroutineScope {

  companion object {
    private val CONTEXT: Key<CoroutineContext> = Key.create(::CONTEXT.qualifiedName<AndroidCoroutinesAware>())
  }

  /** @see AndroidCoroutineScope */
  override val coroutineContext: CoroutineContext
    get() {
      return getUserData(CONTEXT) ?: putUserDataIfAbsent(CONTEXT, AndroidCoroutineScope(this).coroutineContext)
    }
}

/**
 * A coroutine-based launcher that ensures that at most one task is running at any point in time. It cancels the previous task if a new is
 * enqueued.
 */
class UniqueTaskCoroutineLauncher(private val coroutineScope: CoroutineScope, description: String) {
  // This mutex makes sure that the previous job is cancelled before a new one is started. This prevents several jobs to be executed at the
  // same time meaning that several tasks also cannot be executed at the same time, and therefore we do not need a mutex on a task execution
  // itself.
  private val jobMutex = Mutex()

  private val taskDispatcher = AppExecutorUtil.createBoundedApplicationPoolExecutor(description, 1).asCoroutineDispatcher()

  private var taskJob: Job? = null

  /**
   * Returns a coroutine [Job] that wraps the task to be executed. If the task was overridden by the next task during scheduling the
   * returned [Job] is null.
   */
  suspend fun launch(task: suspend () -> Unit): Job? {
    var newJob: Job? = null
    coroutineScope.launch(taskDispatcher) {
      jobMutex.withLock {
        // Cancel any running job and wait for the cancellation to complete
        taskJob?.cancelAndJoin()
        newJob = coroutineScope.launch(taskDispatcher) {
          task()
        }
        taskJob = newJob
      }
    }.join()
    return newJob
  }
}

/**
 * Utility function for creating a scope that is a child of the current scope.
 *
 * * The new scope can optionally be a [supervisor][isSupervisor] scope.
 *
 * * An optional [parentDisposable] can be used to ensure the new scope is
 *   [cancelled][CoroutineScope.cancel] when the [parentDisposable] is
 *   [disposed][Disposer.dispose]. The new scope is, as usual, also cancelled
 *   with its parent scope.
 */
fun CoroutineScope.createChildScope(isSupervisor: Boolean = false,
                                    context: CoroutineContext = EmptyCoroutineContext,
                                    parentDisposable: Disposable? = null): CoroutineScope {
  val newJob = if (isSupervisor) SupervisorJob(this.coroutineContext.job) else Job(this.coroutineContext.job)
  return CoroutineScope(this.coroutineContext + newJob + context).also { newScope ->
    // Attach new scope to [parentDisposable] lifecycle
    parentDisposable?.apply {
      cancelJobOnDispose(parentDisposable, newScope.coroutineContext.job)
    }
  }
}

/**
 * Immediately returns the completed result. If deferred is not complete for any reason, return null.
 */
suspend fun <T> Deferred<T>.getCompletedOrNull(): T? {
  if (isCompleted) {
    return try {
      this.await()
    }
    catch (t: Throwable) {
      null
    }
  }
  return null
}

/**
 * Suspendable method that will suspend until the given [compute] can be executed in a write action in the UI thread.
 */
suspend fun <T> runWriteActionAndWait(compute: Computable<T>): T = coroutineScope {
  val result = CompletableDeferred<T>()
  ApplicationManager.getApplication().invokeLater {
    if (isActive) {
      WriteAction.run<Throwable> {
        if (isActive) result.complete(compute.compute())
      }
    }
  }
  return@coroutineScope result.await()
}

/**
 * Similar to [AndroidPsiUtils#getPsiFileSafely] but using a suspendable function.
 */
suspend fun getPsiFileSafely(project: Project, virtualFile: VirtualFile): PsiFile? = readAction {
  if (project.isDisposed) return@readAction null
  val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction null
  if (psiFile.isValid) psiFile else null
}

/**
 * Scope passed to the runnable in [disposableCallbackFlow].
 */
interface CallbackFlowWithDisposableScope<T> : CoroutineScope {
  /**
   * This disposable will be disposed if the [CoroutineScope] is cancelled or if the optional `parentDisposable` in
   * [disposableCallbackFlow] is disposed.
   */
  val disposable: Disposable

  /**
   * Equivalent to [kotlinx.coroutines.channels.ProducerScope.trySend].
   */
  fun trySend(e: T)
}

/**
 * Similar to [callbackFlow] but allows to use a [Disposable] as part of the callback.
 *
 * The [runnable] will be called with a [CallbackFlowWithDisposableScope] that contains a [Disposable]. The [Disposable] will be disposed if:
 *  - The [parentDisposable] is disposed, if not null.
 *  - The flow is closed.
 *
 * This allows for any callbacks to use that [Disposable] and dispose the listeners when the flow is not needed.
 */
fun <T> disposableCallbackFlow(debugName: String,
                               logger: Logger? = null,
                               parentDisposable: Disposable? = null,
                               runnable: suspend CallbackFlowWithDisposableScope<T>.() -> Unit) = callbackFlow {
  logger?.debug("$debugName start")

  val disposable = parentDisposable?.let {
    // If there is a parent disposable, cancel the flow when it's disposed.
    Disposer.register(it) { cancel("parentDisposable was disposed") }
    Disposer.newDisposable(it, debugName)
  } ?: Disposer.newDisposable(debugName)

  val scope = object : CallbackFlowWithDisposableScope<T> {
    override val coroutineContext: CoroutineContext
      get() = this@callbackFlow.coroutineContext
    override val disposable: Disposable
      get() = disposable

    override fun trySend(e: T) {
      this@callbackFlow.trySend(e)
    }
  }

  scope.runnable()

  awaitClose {
    logger?.debug("$debugName shutdown")
    Disposer.dispose(disposable)
  }
}

/**
 * A [callbackFlow] that produces an element when the [project] moves into smart mode. The [onConnected] listener will be
 * called in the context of a worker thread.
 */
@VisibleForTesting
fun smartModeFlow(project: Project, parentDisposable: Disposable, logger: Logger?, onConnected: (() -> Unit)?): Flow<Unit> =
  disposableCallbackFlow("SmartModeFlow", logger, parentDisposable) {
    val wasInDumbMode = AtomicBoolean(DumbService.getInstance(project).isDumb)
    logger?.debug { "SmartModeFlow wasInDumbMode=${wasInDumbMode.get()}" }
    project.messageBus.connect(disposable).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        // We have detected the change, so clear the flag
        wasInDumbMode.set(false)
        trySend(Unit)
      }
    })

    onConnected?.let { launch(workerThread) { it() } }

    val isInDumbMode = DumbService.getInstance(project).isDumb
    logger?.debug { "SmartModeFlow setup complete wasInDumbMode=${wasInDumbMode.get()} isInDumbMode=${isInDumbMode}" }
    if (wasInDumbMode.getAndSet(false) && !isInDumbMode) {
      trySend(Unit)
    }
  }

/**
 * A [callbackFlow] that produces an element when the [project] moves into smart mode.
 */
fun smartModeFlow(project: Project, parentDisposable: Disposable, logger: Logger? = null): Flow<Unit> =
  smartModeFlow(project, parentDisposable, logger, null)

/**
 * A [callbackFlow] that produces an element when a [PsiFile] changes.
 */
fun psiFileChangeFlow(psiManager: PsiManager, scope: CoroutineScope, logger: Logger? = null, onConnected: (() -> Unit)? = null): Flow<PsiFile> =
  disposableCallbackFlow(debugName = "PsiFileChangeFlow", parentDisposable = scope.scopeDisposable(), logger = logger) {
    psiManager.addPsiTreeChangeListener(
      object : PsiTreeAnyChangeAbstractAdapter() {
        override fun onChange(changedFile: PsiFile?) {
          if (changedFile == null) return
          trySend(changedFile)
        }
      },
      this.disposable
    )

    onConnected?.let { onConnected -> launch(workerThread) { onConnected() } }
  }
    // Avoid repeated change events for no modifications
    .distinctUntilChangedBy { psiManager.modificationTracker.modificationCount }

fun psiFileChangeFlow(project: Project, scope: CoroutineScope, logger: Logger? = null, onConnected: (() -> Unit)? = null): Flow<PsiFile> =
  psiFileChangeFlow(PsiManager.getInstance(project), scope, logger, onConnected)
