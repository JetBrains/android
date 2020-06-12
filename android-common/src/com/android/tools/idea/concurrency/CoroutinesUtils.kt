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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

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

object L {
  val LOG: Logger get() = Logger.getInstance(L::class.java)
}

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
fun AndroidCoroutineScope(disposable: Disposable): CoroutineScope {
  return CoroutineScope(SupervisorJob(disposable) + AndroidDispatchers.workerThread + androidCoroutineExceptionHandler)
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
  override val coroutineContext: CoroutineContext get() {
    return getUserData(CONTEXT) ?: putUserDataIfAbsent(CONTEXT, AndroidCoroutineScope(this).coroutineContext)
  }
}
