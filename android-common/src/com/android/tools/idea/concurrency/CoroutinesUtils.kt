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

import com.android.utils.reflection.qualifiedName
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import kotlinx.coroutines.*
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executor
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
   * [CoroutineDispatcher] that dispatches to an IO thread.
   *
   * @see AndroidExecutors.ioThreadExecutor
   */
  val ioThread: CoroutineDispatcher get() = AndroidExecutors.getInstance().ioThreadExecutor.asCoroutineDispatcher()
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
      L.LOG.error(coroutineName, throwable)
    }
    else {
      L.LOG.error(throwable)
    }
  }
}

/**
 * Creates a [Job] tied to the lifecycle of a [Disposable].
 */
@Suppress("FunctionName") // mirroring upstream API.
fun SupervisorJob(disposable: Disposable): Job {
  return SupervisorJob().also { job ->
    Disposer.register(
      disposable,
      Disposable {
        if (!job.isCancelled) {
          job.cancel(CancellationException("$disposable has been disposed."))
        }
      }
    )
  }
}

/**
 * Returns a [CoroutineScope] containing:
 *   - a [Job] tied to the [Disposable] lifecycle of this object
 *   - [AndroidDispatchers.workerThread]
 *   - a [CoroutineExceptionHandler] that logs unhandled exception at `ERROR` level.
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun AndroidCoroutineScope(disposable: Disposable, context: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
  return CoroutineScope(SupervisorJob(disposable) + AndroidDispatchers.workerThread + androidCoroutineExceptionHandler + context)
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
    private val CONTEXT: Key<CoroutineContext> = Key.create(::CONTEXT.qualifiedName)
  }

  /** @see AndroidCoroutineScope */
  override val coroutineContext: CoroutineContext
    get() {
      return getUserData(CONTEXT) ?: putUserDataIfAbsent(CONTEXT, AndroidCoroutineScope(this).coroutineContext)
    }
}

private val PROJECT_SCOPE: Key<CoroutineScope> = Key.create(::PROJECT_SCOPE.qualifiedName)

val Project.coroutineScope: CoroutineScope
  get() = getUserData(PROJECT_SCOPE) ?: (this as UserDataHolderEx).putUserDataIfAbsent(PROJECT_SCOPE, AndroidCoroutineScope(this))

/**
 * A coroutine-based launcher that ensures that at most one task is running at any point in time. It cancels the previous task if a new is
 * enqueued.
 */
class UniqueTaskCoroutineLauncher(private val coroutineScope: CoroutineScope, description: String) {
  // This mutex makes sure that the previous job is cancelled before a new one is started. This prevents several jobs to be executed at the
  // same time meaning that several tasks also cannot be executed at the same time and therefore we do not need a mutex on a task execution
  // itself.
  private val jobMutex = Mutex()

  private val taskDispatcher = AppExecutorUtil.createBoundedApplicationPoolExecutor(description, 1).asCoroutineDispatcher()

  private var taskJob: Job? = null

  /**
   * Returns a coroutine [Job] that wraps the task to be executed. If the task was overridden by the next task during scheduling the
   * returned [Job] is null.
   */
  suspend fun launch(task: suspend () -> Unit): Job? {
    taskJob?.cancel()
    var newJob: Job? = null
    coroutineScope.launch(taskDispatcher) {
      jobMutex.withLock {
        taskJob?.join()
        newJob = launch(taskDispatcher) {
          task()
        }
        taskJob = newJob
      }
    }.join()
    return newJob
  }
}

/**
 * Utility function for quickly creating a scope that is a child of the current scope. It can be optionally a supervisor scope.
 */
fun CoroutineScope.createChildScope(isSupervisor: Boolean = false): CoroutineScope = CoroutineScope(
  this.coroutineContext + if (isSupervisor) SupervisorJob(this.coroutineContext[Job]) else Job(this.coroutineContext[Job]))

/**
 * Immediately returns the completed result. If deferred is not complete for any reason, return null.
 */
suspend fun <T> Deferred<T>.getCompletedOrNull(): T? {
  if (isCompleted) {
    try {
      return this.await()
    }
    catch (t: Throwable) {
      return null
    }
  }
  return null
}