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
@file:JvmName("FutureUtils")
package com.android.tools.idea.concurrency

import com.google.common.base.Function
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListenableFutureTask
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.SettableFuture
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.EventQueue.isDispatchThread
import java.awt.Toolkit
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * @see Futures.transform
 */
fun <I: Any, O> ListenableFuture<I>.transform(executor: Executor, func: (I) -> O): ListenableFuture<O> {
  return Futures.transform(this, Function<I, O> { i -> func(i!!) }, executor)
}

/**
 * @see Futures.transform
 *
 * This function is useful for interoperability between Java and Kotlin
 * When in Java the future is ListenableFuture<Void> we set a `null` value to complete the future, if that future is used
 * from Kotlin with the [transform] defined above it throws exception because we are assigning null to a non-nullable variable (I)
 */
fun <I, O> ListenableFuture<I>.transformNullable(executor: Executor, func: (I?) -> O): ListenableFuture<O> {
  return Futures.transform(this, Function<I, O> { i -> func(i) }, executor)
}

/**
 * @see Futures.transformAsync
 */
fun <I, O> ListenableFuture<I>.transformAsync(executor: Executor, func: (I) -> ListenableFuture<O>): ListenableFuture<O> {
  return Futures.transformAsync(this, AsyncFunction { i -> func(i!!) }, executor)
}

/**
 * @see Futures.transformAsync
 *
 * This function is useful for interoperability between Java and Kotlin
 * When in Java the future is ListenableFuture<Void> we set a `null` value to complete the future, if that future is used
 * from Kotlin with the [transformAsync] defined above it throws exception because we are assigning null to a non-nullable variable (I)
 */
fun <I, O> ListenableFuture<I>.transformAsyncNullable(executor: Executor, func: (I?) -> ListenableFuture<O>): ListenableFuture<O> {
  return Futures.transformAsync(this, AsyncFunction { i -> func(i) }, executor)
}

/**
 * Wrapper function to convert Future to ListenableFuture
 */
fun <I> Future<I>.listenInPoolThread(executor: Executor): ListenableFuture<I> {
  return JdkFutureAdapters.listenInPoolThread(this, executor)
}

fun <I> List<Future<I>>.listenInPoolThread(executor: Executor): List<ListenableFuture<I>> {
  return this.map { future: Future<I> -> future.listenInPoolThread(executor) }
}

fun <I> List<ListenableFuture<I>>.whenAllComplete(): Futures.FutureCombiner<I?> {
  return Futures.whenAllComplete(this)
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
fun <I> ListenableFuture<I>.addCallback(executor: Executor, success: (I?) -> Unit, failure: (Throwable?) -> Unit) {
  addCallback(executor, object : FutureCallback<I> {
    override fun onFailure(t: Throwable?) {
      failure(t)
    }

    override fun onSuccess(result: I?) {
      success(result)
    }
  })
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
fun <I> ListenableFuture<I>.addCallback(executor: Executor, futureCallback: FutureCallback<I>) {
  Futures.addCallback(this, futureCallback, executor)
}

fun <T> executeOnPooledThread(action: () -> T): ListenableFuture<T> {
  val futureTask = ListenableFutureTask.create(action)
  ApplicationManager.getApplication().executeOnPooledThread(futureTask)
  return futureTask
}

/**
 * Converts a [ListenableFuture] to a [CompletionStage].
 */
fun <T> ListenableFuture<T>.toCompletionStage(): CompletionStage<T> = ListenableFutureToCompletionStageAdapter(this)

fun <T> readOnPooledThread(function: () -> T): ListenableFuture<T> {
  return MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService()).submit<T> { ReadAction.compute<T, Throwable>(function) }
}

private val MyAlarm by lazy {
  Alarm(ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication())
}

fun <V> delayedValue(value: V, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.addRequest({ result.set(value) }, delayMillis)
  return result
}

fun <V> delayedOperation(callable: Callable<V>, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.addRequest(
    Runnable {
      try {
        result.set(callable.call())
      }
      catch (t: Throwable) {
        result.setException(t)
      }
    },
    delayMillis
  )
  return result
}

fun <V> delayedError(t: Throwable, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.addRequest({ result.setException(t) }, delayMillis)
  return result
}

/**
 * Waits on the dispatch thread for a [Future] to complete.
 * Calling this method instead of [Future.get] is required for
 * [Future] that have callbacks executing on the
 * [com.intellij.util.concurrency.EdtExecutorService].
 */
@Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
fun <V> pumpEventsAndWaitForFuture(future: Future<V>, timeout: Long, unit: TimeUnit): V {

  assert(Toolkit.getDefaultToolkit().systemEventQueue is IdeEventQueue)
  assert(isDispatchThread())

  val nano = unit.toNanos(timeout)
  val startNano = System.nanoTime()
  val endNano = startNano + nano

  while (System.nanoTime() <= endNano) {
    IdeEventQueue.getInstance().flushQueue()
    ApplicationManager.getApplication().invokeAndWait(
      Runnable {
        try {
          future.get(50, TimeUnit.MILLISECONDS)
        }
        catch (e: InterruptedException) {
          // Ignore exceptions since we will retry (or rethrow) later on
        }
        catch (e: ExecutionException) {
        }
        catch (e: TimeoutException) {
        }
        catch (e: CancellationException) {
        }
      },
      ModalityState.any()
    )

    if (future.isDone) {
      return future.get()
    }
  }

  throw TimeoutException()
}

/**
 * Similar to [transform], but executes [finallyBlock] in both success and error completion.
 * The returned future fails if:
 * 1. The original future fails.
 * 2. The [finallyBlock] fails.
 *
 * If they both fail, the Throwable from the original future is returned,
 * with the error from [finallyBlock] available through [Throwable.getSuppressed].
 */
fun <I> ListenableFuture<I>.finallySync(executor: Executor, finallyBlock: () -> Unit): ListenableFuture<I> {
  val futureResult = SettableFuture.create<I>()
  val inputFuture = this
  addCallback(executor, object : FutureCallback<I> {
    override fun onSuccess(result: I?) {
      try {
        finallyBlock()
        futureResult.set(result)
      }
      catch (finallyError: Throwable) {
        futureResult.setException(finallyError)
      }
    }

    override fun onFailure(t: Throwable) {
      try {
        finallyBlock()
      }
      catch (finallyThrowable: Throwable) {
        t.addSuppressed(finallyThrowable)
      }

      if (inputFuture.isCancelled) {
        // respect cancellation cause, though we swallow
        // finallyThrowable in this situation
        futureResult.setFuture(inputFuture)
        return
      }
      // propagate original exception with finallyThrowableSuppressed
      futureResult.setException(t)
    }
  })

  futureResult.addCallback(executor, {}) {
    if (futureResult.isCancelled) {
      inputFuture.cancel(true)
    }
  }
  return futureResult
}

/**
 * @see [Futures.catching]
 */
fun <V, X : Throwable> ListenableFuture<out V>.catching(
    executor: Executor, exceptionType: Class<X>, fallback: (X) -> V): ListenableFuture<V> {
  return Futures.catching(this, exceptionType, Function<X, V> { t -> fallback(t!!) }, executor)
}

/**
 * @see [Futures.catchingAsync]
 */
fun <V, X : Throwable> ListenableFuture<out V>.catchingAsync(
    executor: Executor, exceptionType: Class<X>, fallback: (X) -> ListenableFuture<V>): ListenableFuture<V> {
  return Futures.catchingAsync(this, exceptionType, { t -> fallback(t!!) }, executor)
}

/**
 * Submits a [function] in this executor queue, and returns a [ListenableFuture]
 * that completes with the [function] result or the exception thrown from the [function].
 */
fun <V> Executor.executeAsync(function: () -> V): ListenableFuture<V> {
  // Should be migrated to Futures.submit(), once guava will be updated to version >= 28.2
  return Futures.immediateFuture(Unit).transform(this) { function() }
}

/**
 * Cancels this future, when parent [Disposable] is disposed.
 *
 * @return this future to allow chaining
 */
fun <V> ListenableFuture<V>.cancelOnDispose(parent: Disposable): ListenableFuture<V> {
  // best effort but it doesn't guarantee that Disposer.register won't fail
  if (Disposer.isDisposed(parent)) {
    cancel(true)
    return this
  }
  val disposable = Disposable {
    // no-op if future is completed by now.
    cancel(true)
  }
  // on "directExecutor()" usage: disposable that we dispose in this listener is no-op
  // because it is completed by now, so it is relatively safe.
  // It isn't completely safe, because to access the tree Disposer grabs internal lock
  // and it is blocking operation
  addListener({
    if (!Disposer.isDisposed(disposable)) {
      // we need to remove disposable from the tree since we don't need it anymore
      // as well as we need to free future, so it can be gc-ed
      Disposer.dispose(disposable)
    }
  }, directExecutor())
  try {
    Disposer.register(parent, disposable)
  } catch (e: IncorrectOperationException) {
    // parent was disposed in meanwhile, so cancel future
    cancel(true)
  }
  return this
}

/**
 * Tries to get the result of the future without blocking. If result is not ready for any reason, return null.
 */
fun <V> Future<V>.getDoneOrNull(): V? {
  try {
    return Futures.getDone(this)
  }
  catch (e: Exception) {
    return null
  }
}