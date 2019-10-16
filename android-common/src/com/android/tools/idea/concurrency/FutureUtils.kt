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
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListenableFutureTask
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.SettableFuture
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import org.jetbrains.ide.PooledThreadExecutor
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
 * Wrapper function to apply transform function to ListenableFuture after it get done
 */
fun <I, O> ListenableFuture<I>.transform(executor: Executor = directExecutor(), func: (I) -> O): ListenableFuture<O> {
  return Futures.transform(this, Function<I, O> { i -> func(i!!) }, executor)
}

/**
 * Transforms a [ListenableFuture] by throwing out the result.
 */
fun ListenableFuture<*>.ignoreResult(): ListenableFuture<Void?> = transform { null }

/**
 * Wrapper function to convert Future to ListenableFuture
 */
fun <I> Future<I>.listenInPoolThread(executor: Executor = directExecutor()): ListenableFuture<I> {
  return JdkFutureAdapters.listenInPoolThread(this, executor)
}

fun <I> List<Future<I>>.listenInPoolThread(executor: Executor = directExecutor()): List<ListenableFuture<I>> {
  return this.map { future: Future<I> -> future.listenInPoolThread(executor) }
}

fun <I> List<ListenableFuture<I>>.whenAllComplete(): Futures.FutureCombiner<I?> {
  return Futures.whenAllComplete(this)
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), success: (I?) -> Unit, failure: (Throwable?) -> Unit) {
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
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), futureCallback: FutureCallback<I>) {
  Futures.addCallback(this, futureCallback, executor)
}

fun <T> executeOnPooledThread(action: ()->T): ListenableFuture<T> {
  val futureTask = ListenableFutureTask.create(action)
  ApplicationManager.getApplication().executeOnPooledThread(futureTask)
  return futureTask
}

/**
 * Converts a [ListenableFuture] to a [CompletionStage].
 */
fun <T> ListenableFuture<T>.toCompletionStage(): CompletionStage<T> = ListenableFutureToCompletionStageAdapter(this)

fun <T> readOnPooledThread(function: () -> T): ListenableFuture<T> {
  return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<T> { ReadAction.compute<T, Throwable>(function) }
}

private object MyAlarm : AtomicNotNullLazyValue<Alarm>() {
  override fun compute() = Alarm(ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication())
}

fun <V> delayedValue(value: V, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.value.addRequest({ result.set(value) }, delayMillis)
  return result
}

fun <V> delayedOperation(callable: Callable<V>, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.value.addRequest(
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
  MyAlarm.value.addRequest({ result.setException(t) }, delayMillis)
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
