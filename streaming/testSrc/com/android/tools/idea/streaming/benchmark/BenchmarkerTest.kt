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
package com.android.tools.idea.streaming.benchmark

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.streaming.benchmark.Benchmarker.Adapter
import com.android.utils.time.TestTimeSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private const val INPUT_RATE_HZ = 5
private val INPUTS = 1..500
private val numValues = INPUTS.last - INPUTS.first + 1

/** Tests the [Benchmarker] class. */
@RunWith(JUnit4::class)
class BenchmarkerTest {
  private val results: MutableList<Benchmarker.Results<Int>> = mutableListOf()
  private val testTimeSource = TestTimeSource()
  private val mockTimer: Timer = mock()
  private val dispatched: MutableList<Int> = mutableListOf()
  private val adapter = object : Adapter<Int> {
    private val inputsIterator = INPUTS.iterator()
    lateinit var adapterCallbacks: Adapter.Callbacks<Int>
    override fun inputs(): Iterator<Int> = inputsIterator
    override fun numInputs(): Int = numValues
    override fun dispatch(input: Int) { dispatched.add(input) }
    override fun setCallbacks(callbacks: Adapter.Callbacks<Int>) { adapterCallbacks = callbacks }
    override fun ready() { readyCalls++ }
    override fun finalizeInputs() { finalizeInputsCalls++ }
    override fun cleanUp() { cleanUpCalls++ }
  }
  private val benchmarker = createBenchmarker()

  private val progressValues: MutableList<Pair<Double, Double>> = mutableListOf()
  private val failureMessages: MutableList<String> = mutableListOf()

  private var readyCalls: Int = 0
  private var cleanUpCalls: Int = 0
  private var finalizeInputsCalls: Int = 0
  private var stopCallbackCalled = false

  @Test
  fun timerSchedulingStarted() {
    benchmarker.start()

    // We shouldn't start sending events until the adapter is ready.
    verifyNoInteractions(mockTimer)
    adapter.adapterCallbacks.onReady()

    // Should now be in SENDING_INPUTS
    val expectedFrameDurationMillis = (1.seconds / INPUT_RATE_HZ).inWholeMilliseconds
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), eq(0), eq(expectedFrameDurationMillis))
    assertThat(dispatched).isEmpty()

    taskCaptor.value.run()

    assertThat(dispatched).hasSize(1)
    verifyNoMoreInteractions(mockTimer)
  }

  @Test
  fun stop_tearsDownAdapterCallsCallbacksAndCancelsTimer() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    verify(mockTimer).scheduleAtFixedRate(any(), anyLong(), anyLong())

    assertThat(cleanUpCalls).isEqualTo(0)

    benchmarker.stop()

    assertThat(cleanUpCalls).isEqualTo(1)
    verify(mockTimer).cancel()
    verifyNoMoreInteractions(mockTimer)
    assertThat(stopCallbackCalled).isTrue()
    assertThat(failureMessages).containsExactly("Benchmarking was canceled.")
  }

  @Test
  fun timerSchedulingStopped_inputsExhausted() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()

    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(dispatched).isEmpty()
    repeat(numValues) { taskCaptor.value.run() }

    assertThat(dispatched).hasSize(numValues)
    verify(mockTimer).cancel()
    assertThat(failureMessages).isEmpty()
  }

  @Test
  fun finalizeInputsWhenBenchmarkingEnds() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(dispatched).isEmpty()

    repeat(numValues - 1) {taskCaptor.value.run() }
    assertThat(finalizeInputsCalls).isEqualTo(0)
    // One more time should finish it off.
    taskCaptor.value.run()

    assertThat(finalizeInputsCalls).isEqualTo(1)
  }

  @Test
  fun finalizeInputsWhenBenchmarkingCanceled() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(dispatched).isEmpty()

    // Just one input
    taskCaptor.value.run()
    assertThat(finalizeInputsCalls).isEqualTo(0)
    benchmarker.stop()

    assertThat(finalizeInputsCalls).isEqualTo(1)
  }

  @Test
  fun failedToGetReady() {
    benchmarker.start()
    assertThat(stopCallbackCalled).isFalse()

    val failureMessage = "This thing failed to become ready!"
    adapter.adapterCallbacks.onFailedToBecomeReady(failureMessage)

    assertThat(stopCallbackCalled).isTrue()
    assertThat(failureMessages).containsExactly(failureMessage)
  }

  @Test
  fun isDone() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())

    repeat(numValues + 1) { taskCaptor.value.run() }

    assertThat(dispatched).hasSize(numValues)
    dispatched.forEach {
      adapter.adapterCallbacks.inputReturned(it, testTimeSource.markNow())
    }

    // All inputs should be received now, so we should be in state COMPLETE.
    assertThat(benchmarker.isDone()).isTrue()
    assertThat(stopCallbackCalled).isTrue()
    assertThat(results).isNotEmpty()
    assertThat(failureMessages).isEmpty()
    verify(mockTimer).cancel()
    assertThat(results).hasSize(1)
    assertThat(results[0].raw).hasSize(numValues)
    assertThat(results[0].raw.values.toSet()).containsExactly(Duration.ZERO)
    results[0].percentiles.values.forEach {
      assertThat(it).isWithin(0.0000000001).of(0.0)
    }
  }

  @Test
  fun computesResultsCorrectly() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())
    assertThat(dispatched).isEmpty()

    repeat(numValues) {
      taskCaptor.value.run()
      testTimeSource += it.seconds  // Each input will take 1s longer than the last.
      adapter.adapterCallbacks.inputReturned(dispatched.last(), testTimeSource.markNow())
    }
    assertThat(dispatched).hasSize(numValues)
    assertThat(benchmarker.isDone()).isTrue()
    val expectedRawResults = (0 until numValues).associate { dispatched[it] to it.seconds }
    assertThat(results[0].raw).containsExactlyEntriesIn(expectedRawResults)
    // This distribution just increases linearly, so each percentile is a relative fraction of the max.
    val maxDurationMillis = (numValues - 1).seconds.toDouble(DurationUnit.MILLISECONDS)
    results[0].percentiles.forEach { (k, v) ->
      assertThat(v).isWithin(0.00000001).of(maxDurationMillis * k / 100)
    }
  }

  @Test
  fun callsOnProgressCallbacks() {
    benchmarker.start()
    adapter.adapterCallbacks.onReady()
    val taskCaptor: ArgumentCaptor<TimerTask> = argumentCaptor()
    verify(mockTimer).scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong())

    repeat(numValues + 1) { taskCaptor.value.run() }

    assertThat(dispatched).hasSize(numValues)

    dispatched.forEach {
      adapter.adapterCallbacks.inputReturned(it, testTimeSource.markNow())
    }

    // The first one is a 0,0 we always set to clear the progress bar.
    val values = progressValues.drop(1)
    assertThat(values).hasSize(dispatched.size)
    assertThat(values.map{it.second}).isStrictlyOrdered()
    assertThat(values.first().second).isWithin(0.000001).of(1 / dispatched.size.toDouble())
    assertThat(values.last().second).isWithin(0.000001).of(1.0)
    values.map{it.first}.forEach { assertThat(it).isWithin(0.000001).of(1.0) }
  }

  private fun createBenchmarker(inputRateHz: Int = INPUT_RATE_HZ) =
    Benchmarker(adapter, inputRateHz, testTimeSource, mockTimer).apply {
      addCallbacks(object: Benchmarker.Callbacks<Int> {
        override fun onProgress(dispatched: Double, returned: Double) { progressValues.add(dispatched to returned) }
        override fun onStopped() { stopCallbackCalled = true }
        override fun onFailure(failureMessage: String) { failureMessages.add(failureMessage) }
        override fun onComplete(results: Benchmarker.Results<Int>) { this@BenchmarkerTest.results.add(results) }
      })
    }

}
