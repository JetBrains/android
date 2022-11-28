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

import com.android.testutils.MockitoKt
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class JfrManifestMergerReportsTest {

  private val mockStartCapture: () -> Unit = MockitoKt.mock()
  private val mockStopCapture: () -> Unit = MockitoKt.mock()

  private val fakeScheduler = VirtualTimeScheduler()

  @Test
  fun snapshotComputeListener_computeEndsBeforeThreshold() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Start the merge
    val token = Object()
    val startTimeMillis = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token, startTimeMillis)
    fakeScheduler.advanceBy(0)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // End the merge just before the threshold
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 1L, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(token, startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Advance the clock to ensure no captures are started.
    fakeScheduler.advanceBy(1, TimeUnit.DAYS)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)
  }

  @Test
  fun snapshotComputeListener_computeEndsAfterThreshold() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Start the merge
    val token = Object()
    val startTimeMillis = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token, startTimeMillis)
    fakeScheduler.advanceBy(0)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Advance to threshold
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong(), TimeUnit.MILLISECONDS)

    verify(mockStartCapture, times(1)).invoke()
    verifyNoInteractions(mockStopCapture)

    // End the merge
    listener.snapshotCreationEnded(token, startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(0)

    verify(mockStartCapture, times(1)).invoke()
    verify(mockStopCapture, times(1)).invoke()
  }

  @Test
  fun snapshotComputeListener_multipleSequentialComputes() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Merge 0: below threshold
    val token0 = Object()
    val startTimeMillis0 = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token0, startTimeMillis0)
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(token0, startTimeMillis0, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(1, TimeUnit.SECONDS)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Merge 1: above threshold
    val token1 = Object()
    val startTimeMillis1 = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token1, startTimeMillis1)
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() + 1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(token1, startTimeMillis1, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(1, TimeUnit.SECONDS)

    verify(mockStartCapture, times(1)).invoke()
    verify(mockStopCapture, times(1)).invoke()

    // Merge 2: above threshold
    val token2 = Object()
    val startTimeMillis2 = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token2, startTimeMillis2)
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() + 1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(token2, startTimeMillis2, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(1, TimeUnit.SECONDS)

    verify(mockStartCapture, times(2)).invoke()
    verify(mockStopCapture, times(2)).invoke()

    // Merge 3: below threshold
    val token3 = Object()
    val startTimeMillis3 = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(token3, startTimeMillis3)
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(token3, startTimeMillis3, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(1, TimeUnit.SECONDS)

    verify(mockStartCapture, times(2)).invoke()
    verify(mockStopCapture, times(2)).invoke()
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesBelowThreshold() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Start 4 merges at the same time.
    val tokens = listOf(Object(), Object(), Object(), Object())
    val startTimeMillis = fakeScheduler.currentTimeMillis

    listener.snapshotCreationStarted(tokens[0], startTimeMillis)
    listener.snapshotCreationStarted(tokens[1], startTimeMillis)
    listener.snapshotCreationStarted(tokens[2], startTimeMillis)
    listener.snapshotCreationStarted(tokens[3], startTimeMillis)

    // End the merges out of order, all before the reporting threshold.
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 10, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[3], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[0], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[2], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[1], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    // Advance the clock to ensure no captures are started.
    fakeScheduler.advanceBy(1, TimeUnit.DAYS)

    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesWithOneAboveThreshold() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Start 4 merges at the same time.
    val tokens = listOf(Object(), Object(), Object(), Object())
    val startTimeMillis = fakeScheduler.currentTimeMillis

    listener.snapshotCreationStarted(tokens[0], startTimeMillis)
    listener.snapshotCreationStarted(tokens[1], startTimeMillis)
    listener.snapshotCreationStarted(tokens[2], startTimeMillis)
    listener.snapshotCreationStarted(tokens[3], startTimeMillis)

    // End the merges out of order, with three before the reporting threshold.
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 10, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[3], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[0], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[2], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    // Advance by 10ms, which pushes the last merge above the threshold.
    fakeScheduler.advanceBy(10, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[1], startTimeMillis, fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(0)

    verify(mockStartCapture, times(1)).invoke()
    verify(mockStopCapture, times(1)).invoke()
  }

  @Test
  fun snapshotComputeListener_multipleParallelComputesWithMoreThanOneAboveThreshold() {
    val listener = JfrManifestMergerReports.MyMergedManifestSnapshotComputeListener(mockStartCapture, mockStopCapture, fakeScheduler)

    // Start 4 merges at staggered times.
    val tokens = listOf(Object(), Object(), Object(), Object())
    val startTimes = mutableListOf<Long>(0, 0, 0, 0)

    startTimes[0] = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(tokens[0], startTimes[0])

    fakeScheduler.advanceBy(10, TimeUnit.MILLISECONDS)
    startTimes[1] = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(tokens[1], startTimes[1])

    fakeScheduler.advanceBy(10, TimeUnit.MILLISECONDS)
    startTimes[2] = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(tokens[2], startTimes[2])

    fakeScheduler.advanceBy(10, TimeUnit.MILLISECONDS)
    startTimes[3] = fakeScheduler.currentTimeMillis
    listener.snapshotCreationStarted(tokens[3], startTimes[3])

    // End merge 0 before the threshold.
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong() - 31, TimeUnit.MILLISECONDS)
    listener.snapshotCreationEnded(tokens[0], startTimes[0], fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)

    // Advance to just before merge 1 reaches the threshold. No report should have started yet.
    fakeScheduler.advanceBy(10, TimeUnit.MILLISECONDS)
    verifyNoInteractions(mockStartCapture)
    verifyNoInteractions(mockStopCapture)

    // Now allow merge 1 to reach the threshold.
    fakeScheduler.advanceBy(1, TimeUnit.MILLISECONDS)
    verify(mockStartCapture, times(1)).invoke()
    verifyNoInteractions(mockStopCapture)

    // Allow merges 2 and 3 to reach the threshold as well. No more captures should be started.
    fakeScheduler.advanceBy(JfrManifestMergerReports.reportingThresholdMillis.toLong(), TimeUnit.MILLISECONDS)
    verify(mockStartCapture, times(1)).invoke()
    verifyNoInteractions(mockStopCapture)

    // End merge 2. The report should not stop, since merge 1 is the controlling merge.
    listener.snapshotCreationEnded(tokens[2], startTimes[2], fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(0)
    verify(mockStartCapture, times(1)).invoke()
    verifyNoInteractions(mockStopCapture)

    // End merge 1. Now the report should stop.
    listener.snapshotCreationEnded(tokens[1], startTimes[1], fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(0)
    verify(mockStartCapture, times(1)).invoke()
    verify(mockStopCapture, times(1)).invoke()

    // End merge 3. No more calls should have come in to stop, since this merge didn't cause a report to be started.
    listener.snapshotCreationEnded(tokens[3], startTimes[3], fakeScheduler.currentTimeMillis, MergeResult.SUCCESS)
    fakeScheduler.advanceBy(0)
    verify(mockStartCapture, times(1)).invoke()
    verify(mockStopCapture, times(1)).invoke()
  }
}