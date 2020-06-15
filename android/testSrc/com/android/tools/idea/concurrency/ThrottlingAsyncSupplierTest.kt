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

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.RuntimeException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.test.fail

@RunWith(JUnit4::class)
class ThrottlingAsyncSupplierTest {
  @get:Rule
  val disposableRule = DisposableRule()

  private fun <V : Disposable> V.disposeAfterTest(): V {
    Disposer.register(disposableRule.disposable, this)
    return this
  }

  /**
   * Creates a simple [ThrottlingAsyncSupplier] with the given [isUpToDate] function whose
   * compute function returns the next positive integer each time it's called.
   */
  private fun createPositiveIntegerSupplier(isUpToDate: (Int) -> Boolean) = ThrottlingAsyncSupplier(
    compute = generateSequence(1) { it + 1 }.iterator()::next,
    isUpToDate = isUpToDate,
    mergingPeriod = Duration.ofMillis(1)
  ).disposeAfterTest()

  private fun <T> Future<T>.assertCompletesExceptionally(cause: Throwable) {
    try {
      get()
    } catch (e: ExecutionException) {
      assertThat(e.cause).isEqualTo(cause)
      return
    }
    fail("Future was not completed exceptionally as excepted.")
  }

  @Test
  fun now_matchesLastComputation() {
    val supplier = createPositiveIntegerSupplier { false }
    assertThat(supplier.now).isNull()
    supplier.get().get()
    assertThat(supplier.now).isEqualTo(1)
    supplier.get().get()
    assertThat(supplier.now).isEqualTo(2)
  }

  @Test
  fun modificationCount_increasesWithComputations() {
    val supplier = createPositiveIntegerSupplier { false }

    var lastModificationCount = supplier.modificationCount
    assertThat(supplier.modificationCount).isEqualTo(lastModificationCount)

    supplier.get().get()
    lastModificationCount = supplier.modificationCount.also {
      assertThat(it).isGreaterThan(lastModificationCount)
    }

    supplier.now
    assertThat(supplier.modificationCount).isEqualTo(lastModificationCount)

    supplier.get().get()
    lastModificationCount = supplier.modificationCount.also {
      assertThat(it).isGreaterThan(lastModificationCount)
    }
  }

  @Test
  fun get_propagatesException() {
    val computeError = RuntimeException("computation failed")
    val supplier = ThrottlingAsyncSupplier<Any>(
      compute = { throw computeError },
      isUpToDate = { false },
      mergingPeriod = Duration.ofMillis(1)
    ).disposeAfterTest()

    supplier.get().assertCompletesExceptionally(computeError)
    assertThat(supplier.now).isNull()
    assertThat(supplier.modificationCount).isLessThan(0L)
  }

  @Test
  fun get_returnsCachedValueIfFresh() {
    val supplier = createPositiveIntegerSupplier { true }
    assertThat(supplier.get().get()).isEqualTo(1)
    assertThat(supplier.get().get()).isEqualTo(1)
  }

  @Test
  fun get_recomputesIfStale() {
    val supplier = createPositiveIntegerSupplier { false }
    assertThat(supplier.get().get()).isEqualTo(1)
    assertThat(supplier.get().get()).isEqualTo(2)
  }

  @Test
  fun get_mergesSuccessiveRequests() {
    val supplier = ThrottlingAsyncSupplier(
      compute = generateSequence(System::currentTimeMillis).iterator()::next,
      isUpToDate = { false },
      mergingPeriod = Duration.ofMillis(100)
    ).disposeAfterTest()

    // Ideally we wouldn't rely on the assumption that we can call get() several times before
    // the Alarm is finished waiting, but there doesn't seem to be a good way to get around this.
    val merged = Futures.allAsList(
      (1..5).map { supplier.get() }
    ).get()
    val timeBetweenComputations = supplier.get().get() - merged[0]

    // All the futures returned by get() during the first merging period should end up with the same value.
    assertThat(merged.toSet().size).isEqualTo(1)
    // The next computation must not start until after the second merging period.
    assertThat(timeBetweenComputations).isAtLeast(100)
  }

  @Test
  fun get_mergesSuccessiveRequestsWithException() {
    // Define a compute function which fails on its first invocation and
    // returns the system time for any subsequent invocations.
    val computeError = RuntimeException("computation failed")
    var firstComputeTime: Long? = null
    val getTime = {
      if (firstComputeTime == null) {
        firstComputeTime = System.currentTimeMillis()
        throw computeError
      }
      System.currentTimeMillis()
    }
    val supplier = ThrottlingAsyncSupplier(
      compute = getTime,
      isUpToDate = { false },
      mergingPeriod = Duration.ofMillis(100)
    ).disposeAfterTest()

    // The successive calls to get() should be merged into a single invocation
    //  of getTime() which completes exceptionally.
    (1..5).map { supplier.get() }
      .forEach { it.assertCompletesExceptionally(computeError) }

    // Ensure that we wait the full merging period before invoking getTime() again.
    val timeBetweenComputations = supplier.get().get() - firstComputeTime!!
    assertThat(timeBetweenComputations).isAtLeast(100)
  }

  @Test
  fun runScheduledComputation_usesFreshCachedValue() {
    val firstComputationStarted = CountDownLatch(1)
    val secondComputationRequested = CountDownLatch(1)
    val positiveIntegers = generateSequence(1) { it + 1 }.iterator()
    val supplier = ThrottlingAsyncSupplier(
      compute = {
        firstComputationStarted.countDown()
        secondComputationRequested.await()
        return@ThrottlingAsyncSupplier positiveIntegers.next()
      },
      isUpToDate = { true },
      mergingPeriod = Duration.ofMillis(1)
    ).disposeAfterTest()

    val firstComputation = supplier.get()
    firstComputationStarted.await()

    // Calling get() while the first computation is still executing will cause a second
    // computation to be scheduled since the cached value is still null.
    val secondComputation = supplier.get()
    secondComputationRequested.countDown()

    // Since the first computation produces a value that's still valid when it comes
    // time to perform the second computation, we should just reuse the first value.
    assertThat(firstComputation.get()).isEqualTo(secondComputation.get())
  }

  @Test
  fun runScheduledComputation_ignoresStaleCachedValue() {
    val firstComputationStarted = CountDownLatch(1)
    val secondComputationRequested = CountDownLatch(1)
    val positiveIntegers = generateSequence(1) { it + 1 }.iterator()
    val supplier = ThrottlingAsyncSupplier(
      compute = {
        firstComputationStarted.countDown()
        secondComputationRequested.await()
        return@ThrottlingAsyncSupplier positiveIntegers.next()
      },
      isUpToDate = { false },
      mergingPeriod = Duration.ofMillis(1)
    ).disposeAfterTest()

    val firstComputation = supplier.get()
    firstComputationStarted.await()

    // Calling get() while the first computation is still executing will cause a second
    // computation to be scheduled since the cached value is still null.
    val secondComputation = supplier.get()
    secondComputationRequested.countDown()

    // Since the first computation produces a value that's no longer valid when it comes
    // time to perform the second computation, we need to recompute.
    assertThat(firstComputation.get()).isLessThan(secondComputation.get())
  }

  @Test
  fun runScheduledComputation_ignoresAlreadyCheckedCachedValue() {
    var isUpToDateCalled = false
    val supplier = ThrottlingAsyncSupplier(
      compute = generateSequence(1) { it + 1 }.iterator()::next,
      isUpToDate = {
        if (!isUpToDateCalled) {
          isUpToDateCalled = true
          return@ThrottlingAsyncSupplier false
        } else {
          fail()
        }
      },
      mergingPeriod = Duration.ofMillis(1)
    ).disposeAfterTest()
    // On the first call to get, we always compute the value without calling isUpToDate()
    supplier.get().get()
    // Since the first computation has already finished, the cached value will remain the same
    // between the call to get() in the calling thread and the call to runScheduledComputation()
    // in the background. We should detect this case and not waste time calling isUpToDate() again.
    supplier.get().get()
  }
}