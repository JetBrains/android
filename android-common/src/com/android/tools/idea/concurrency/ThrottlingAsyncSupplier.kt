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

import com.android.annotations.concurrency.AnyThread
import com.android.utils.concurrency.AsyncSupplier
import com.android.utils.concurrency.CachedAsyncSupplier
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse.POOLED_THREAD
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * A caching [AsyncSupplier] which ensures an expensive-to-compute value isn't computed more than once in
 * a set period. It makes sense to use a [ThrottlingAsyncSupplier] when you expect [AsyncSupplier.get]
 * to be called more frequently than what's reasonable given time or resource constraints.
 *
 * [ThrottlingAsyncSupplier] works by delaying computation in order to merge rapid successions of
 * [AsyncSupplier.get] requests. It guarantees that the computed value reflects the state of the world
 * at or after the time that [AsyncSupplier.get] was called. This is in contrast to [CachedAsyncSupplier],
 * for which [AsyncSupplier.get] may return the future corresponding to an already in-progress computation.
 */
class ThrottlingAsyncSupplier<V : Any>(
  private val compute: () -> V,
  @AnyThread private val isUpToDate: (value: V) -> Boolean,
  private val mergingPeriod: Duration
) : AsyncSupplier<V>, Disposable, ModificationTracker {

  private val alarm = Alarm(POOLED_THREAD, this)
  private val scheduledComputation = AtomicReference<Computation<V>?>(null)
  /**
   * The completed [Computation] representing the last successful invocation of [compute].
   * Our implementation of [now] simply returns the result of this [Computation].
   */
  private val lastSuccessfulComputation = AtomicReference<Computation<V>?>(null)
  /**
   * The completed [Computation] representing the last invocation of [compute] that
   * encountered an exception during execution.
   * We use the timestamp of this [Computation] later in [determineDelay] to avoid
   * waiting longer than we need to for guaranteeing the [mergingPeriod]
   * between invocations.
   */
  private val lastFailedComputation = AtomicReference<Computation<V>?>(null)

  override fun dispose() {
  }

  /**
   * Returns a modification count corresponding to changes in the value of [now].
   *
   * The count is updated for each successful invocation of [compute] triggered by this supplier,
   * and invocations that throw an exception are ignored.
   */
  override fun getModificationCount() = lastSuccessfulComputation.get()?.let { it.modificationCountWhenScheduled + 1 } ?: -1L

  /**
   * Returns the result of the last successful invocation of [compute] triggered by this supplier
   * (invocations that throw an exception are ignored).
   * @see AsyncSupplier.now
   */
  override val now: V?
    get() = lastSuccessfulComputation.get()?.getResultNow()

  override fun get(): ListenableFuture<V> {
    val cachedComputation = lastSuccessfulComputation.get()
    val cachedValue = cachedComputation?.getResultNow()
    if (cachedValue != null && isUpToDate(cachedValue)) {
      return Futures.immediateFuture(cachedValue)
    }
    val computation = Computation<V>(modificationCount)
    val scheduled = scheduledComputation.compareAndExchange(null, computation)
    if (scheduled == null) {
      // Our thread won and our computation is considered scheduled. Let's schedule it:
      alarm.addRequest(this::runScheduledComputation, determineDelay(cachedComputation, lastFailedComputation.get()))
      return computation.getResult()
    }
    return scheduled.getResult()
  }

  private fun runScheduledComputation() {
    // After this point, any subsequent calls to get() will be merged into a newly-scheduled computation.
    // The Alarm uses a bounded thread pool of size 1, so runScheduledComputation() is never running in
    // more than one thread at a time. Since this is the only place scheduledComputation is set to null,
    // we know the computation is non-null.
    val computation = scheduledComputation.getAndSet(null)!!
    // It's possible that this computation was scheduled while we were in the middle of
    // another computation. In that case, we haven't yet checked the freshness of the result
    // of the first computation, so we should see if it's still valid before recomputing.
    val cachedComputation = lastSuccessfulComputation.get()
    if (cachedComputation != null
        && cachedComputation.modificationCountWhenScheduled == computation.modificationCountWhenScheduled
        && isUpToDate(cachedComputation.getResultNow())) {
      computation.complete(cachedComputation.getResultNow())
      computation.broadcastResult()
      return
    }
    // Set lastComputation before broadcasting the result since we don't know how long
    // that will take and we want to offer getNow() callers as fresh a value as possible.
    val result: V
    try {
      result = compute()
    }
    catch (t: Throwable) {
      computation.completeExceptionally(t)
      lastFailedComputation.set(computation)
      computation.broadcastResult()
      return
    }
    computation.complete(result)
    lastSuccessfulComputation.set(computation)
    computation.broadcastResult()
  }

  private fun determineDelay(lastSuccessfulComputation: Computation<V>?, lastFailedComputation: Computation<V>?): Long {
    val lastComputationTime = maxOf(
      lastSuccessfulComputation?.getCompletionTimestamp() ?: -1L,
      lastFailedComputation?.getCompletionTimestamp() ?: -1L
    )
    if (lastComputationTime < 0) {
      return mergingPeriod.toMillis()
    }
    val timeSinceLastComputation = System.currentTimeMillis() - lastComputationTime
    return (mergingPeriod.toMillis() - timeSinceLastComputation).coerceAtLeast(0L)
  }
}

private sealed class ComputationResult<V> {
  val timestampMs = System.currentTimeMillis()
  data class Value<V>(val value: V) : ComputationResult<V>()
  data class Error<V>(val error: Throwable) : ComputationResult<V>()
}

private class Computation<V>(val modificationCountWhenScheduled: Long) {
  private val result = AtomicReference<ComputationResult<V>>(null)
  private val future = SettableFuture.create<V>()

  private fun getResultAndCheckComplete(): ComputationResult<V> {
    return result.get() ?: throw IllegalStateException("This Computation hasn't been executed yet.")
  }

  private fun complete(result: ComputationResult<V>) {
    check(this.result.compareAndSet(null, result)) { "This Computation has already been executed." }
  }

  fun complete(result: V) = complete(ComputationResult.Value(result))

  fun completeExceptionally(error: Throwable) = complete(ComputationResult.Error(error))

  fun broadcastResult() {
    val result = getResultAndCheckComplete()
    if (result is ComputationResult.Value) {
      future.set(result.value)
    }
    else {
      future.setException((result as ComputationResult.Error).error)
    }
  }

  fun getResult() = Futures.nonCancellationPropagating(future)!!

  fun getResultNow(): V {
    val result = getResultAndCheckComplete()
    if (result is ComputationResult.Value) {
      return result.value
    }
    throw IllegalStateException("This Computation completed exceptionally.", (result as ComputationResult.Error).error)
  }

  fun getCompletionTimestamp() = getResultAndCheckComplete().timestampMs
}