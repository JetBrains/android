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
package com.android.tools.idea.diagnostics.jfr.reports

import com.android.tools.idea.diagnostics.jfr.reports.JfrManifestMergerReports.REPORTING_THRESHOLD
import com.android.tools.idea.diagnostics.jfr.reports.JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class JfrManifestMergerReportsTest {
  private val scheduler = TestCoroutineScheduler()
  private val dispatcher = StandardTestDispatcher(scheduler)
  private val testScope = TestScope(dispatcher)

  private var startCaptureCalls = 0
  private var stopCaptureCalls = 0

  private val listener = MyMergedManifestSnapshotComputeListener({ ++startCaptureCalls }, { ++stopCaptureCalls }, testScope)
  @Test
  fun snapshotComputeListener_computeEndsBeforeThreshold() {
    // Start the merge
    val token = Any()
    val startTimeMillis = scheduler.currentTime
    listener.snapshotCreationStarted(token, startTimeMillis)
    scheduler.runCurrent()

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // End the merge just before the threshold
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 1.milliseconds)
    listener.snapshotCreationEnded(token, startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // Advance the clock to ensure no captures are started.
    scheduler.advanceTimeBy(1.days)

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)
  }

  @Test
  fun snapshotComputeListener_computeEndsAfterThreshold() {
    // Start the merge
    val token = Any()
    val startTimeMillis = scheduler.currentTime
    listener.snapshotCreationStarted(token, startTimeMillis)
    scheduler.runCurrent()

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // Advance to the threshold
    scheduler.advanceTimeBy(REPORTING_THRESHOLD)
    scheduler.runCurrent()

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // End the merge
    listener.snapshotCreationEnded(token, startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.runCurrent()

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(1)
  }

  @Test
  fun snapshotComputeListener_multipleSequentialComputes() {
    // Merge 0: below threshold
    val token0 = Any()
    val startTimeMillis0 = scheduler.currentTime
    listener.snapshotCreationStarted(token0, startTimeMillis0)
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 1.milliseconds)
    listener.snapshotCreationEnded(token0, startTimeMillis0, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.seconds)

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // Merge 1: above threshold
    val token1 = Any()
    val startTimeMillis1 = scheduler.currentTime
    listener.snapshotCreationStarted(token1, startTimeMillis1)
    scheduler.advanceTimeBy(REPORTING_THRESHOLD + 1.milliseconds)
    listener.snapshotCreationEnded(token1, startTimeMillis1, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.seconds)

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(1)

    // Merge 2: above threshold
    val token2 = Any()
    val startTimeMillis2 = scheduler.currentTime
    listener.snapshotCreationStarted(token2, startTimeMillis2)
    scheduler.advanceTimeBy(REPORTING_THRESHOLD + 1.milliseconds)
    listener.snapshotCreationEnded(token2, startTimeMillis2, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.seconds)

    assertThat(startCaptureCalls).isEqualTo(2)
    assertThat(stopCaptureCalls).isEqualTo(2)

    // Merge 3: below threshold
    val token3 = Any()
    val startTimeMillis3 = scheduler.currentTime
    listener.snapshotCreationStarted(token3, startTimeMillis3)
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 1.milliseconds)
    listener.snapshotCreationEnded(token3, startTimeMillis3, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.seconds)

    assertThat(startCaptureCalls).isEqualTo(2)
    assertThat(stopCaptureCalls).isEqualTo(2)
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesBelowThreshold() {
    // Start 4 merges at the same time.
    val tokens = List(4) { Any() }
    val startTimeMillis = scheduler.currentTime
    tokens.forEach { listener.snapshotCreationStarted(it, startTimeMillis) }

    // End the merges out of order, all before the reporting threshold.
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 10.milliseconds)
    listener.snapshotCreationEnded(tokens[3], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.milliseconds)
    listener.snapshotCreationEnded(tokens[0], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.milliseconds)
    listener.snapshotCreationEnded(tokens[2], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.milliseconds)
    listener.snapshotCreationEnded(tokens[1], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)

    // Advance the clock to ensure no captures are started.
    scheduler.advanceTimeBy(1.days)

    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesWithOneAboveThreshold() {
    // Start 4 merges at the same time.
    val tokens = List(4) { Any() }
    val startTimeMillis = scheduler.currentTime
    tokens.forEach { listener.snapshotCreationStarted(it, startTimeMillis) }

    // End the merges out of order, with three before the reporting threshold.
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 10.milliseconds)
    listener.snapshotCreationEnded(tokens[3], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.milliseconds)
    listener.snapshotCreationEnded(tokens[0], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.advanceTimeBy(1.milliseconds)
    listener.snapshotCreationEnded(tokens[2], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)
    // Advance by 10ms, which pushes the last merge above the threshold.
    scheduler.advanceTimeBy(10.milliseconds)
    listener.snapshotCreationEnded(tokens[1], startTimeMillis, scheduler.currentTime, MergeResult.SUCCESS)

    scheduler.runCurrent()

    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(1)
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesWithMoreThanOneAboveThreshold() {
    // Start 4 merges at staggered times.
    val tokens = List(4) { Any() }
    val startTimes = mutableListOf<Long>()
    tokens.forEach {
      startTimes.add(scheduler.currentTime)
      listener.snapshotCreationStarted(it, scheduler.currentTime)
      scheduler.advanceTimeBy(10.milliseconds)
    }

    // End merge 0 before the threshold.
    scheduler.advanceTimeBy(REPORTING_THRESHOLD - 41.milliseconds)
    listener.snapshotCreationEnded(tokens[0], startTimes[0], scheduler.currentTime, MergeResult.SUCCESS)

    // Advance to when merge 1 reaches the threshold. No report should have started yet.
    scheduler.advanceTimeBy(11.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(0)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // Now allow merge 1 to pass the threshold.
    scheduler.advanceTimeBy(1.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // Allow merges 2 and 3 to pass the threshold as well. No more captures should be started.
    scheduler.advanceTimeBy(REPORTING_THRESHOLD + 1.milliseconds)
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // End merge 2. The report should not stop, since merge 1 is the controlling merge.
    listener.snapshotCreationEnded(tokens[2], startTimes[2], scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.runCurrent()
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(0)

    // End merge 1. Now the report should stop.
    listener.snapshotCreationEnded(tokens[1], startTimes[1], scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.runCurrent()
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(1)

    // End merge 3. No more calls should have come in to stop, since this merge didn't cause a report to be started.
    listener.snapshotCreationEnded(tokens[3], startTimes[3], scheduler.currentTime, MergeResult.SUCCESS)
    scheduler.runCurrent()
    assertThat(startCaptureCalls).isEqualTo(1)
    assertThat(stopCaptureCalls).isEqualTo(1)
  }

  private fun TestCoroutineScheduler.advanceTimeBy(duration: Duration) {
    advanceTimeBy(duration.inWholeMilliseconds)
  }
}
