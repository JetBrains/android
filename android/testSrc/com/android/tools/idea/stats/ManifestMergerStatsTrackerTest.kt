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
package com.android.tools.idea.stats

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult.CANCELED
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult.FAILED
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult.SUCCESS
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(JUnit4::class)
class ManifestMergerStatsTrackerTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  private lateinit var testUsageTrackerWriter: TestUsageTracker

  @Before
  fun setup() {
    // ManifestMergerStatsTracker is a static singleton object, and previous tests may have sent it data if they triggered manifest merge.
    // Calling report here causes any existing stats to be cleared out before we begin the test, giving consistent state.
    ManifestMergerStatsTracker.reportMergerStats()

    testUsageTrackerWriter = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(testUsageTrackerWriter)
  }

  @After
  fun teardown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun reportMergerStats_noRunTimes() {
    ManifestMergerStatsTracker.reportMergerStats()

    assertThat(testUsageTrackerWriter.usages).isEmpty()
  }

  @Test
  fun reportMergerStats_onlySuccessRunTimes() {
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), SUCCESS)

    // Flush any remaining runnables on the event thread, since recording events happens asynchronously there.
    ApplicationManager.getApplication().invokeAndWait {}

    ManifestMergerStatsTracker.reportMergerStats()

    assertThat(testUsageTrackerWriter.usages).hasSize(1)
    val loggedEvent = testUsageTrackerWriter.usages[0].studioEvent

    assertThat(loggedEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.MANIFEST_MERGER_STATS)
    assertThat(loggedEvent.hasManifestMergerStats()).isTrue()
    assertThat(loggedEvent.manifestMergerStats.hasSuccessRunTimeMs()).isTrue()
    assertThat(loggedEvent.manifestMergerStats.hasCanceledRunTimeMs()).isFalse()
    assertThat(loggedEvent.manifestMergerStats.hasFailedRunTimeMs()).isFalse()

    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.totalCount).isEqualTo(5)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList).hasSize(1)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].start).isEqualTo(20)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].end).isEqualTo(21)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].samples).isEqualTo(5)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].totalSamples).isEqualTo(5)
  }

  @Test
  fun reportMergerStats_allThreeResultTypes() {
    ManifestMergerStatsTracker.recordManifestMergeRunTime(40.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(40.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(30.toDuration(DurationUnit.MILLISECONDS), CANCELED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(30.toDuration(DurationUnit.MILLISECONDS), CANCELED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), FAILED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), FAILED)

    // Flush any remaining runnables on the event thread, since recording events happens asynchronously there.
    ApplicationManager.getApplication().invokeAndWait {}

    ManifestMergerStatsTracker.reportMergerStats()

    assertThat(testUsageTrackerWriter.usages).hasSize(1)
    val loggedEvent = testUsageTrackerWriter.usages[0].studioEvent

    assertThat(loggedEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.MANIFEST_MERGER_STATS)
    assertThat(loggedEvent.hasManifestMergerStats()).isTrue()
    assertThat(loggedEvent.manifestMergerStats.hasSuccessRunTimeMs()).isTrue()
    assertThat(loggedEvent.manifestMergerStats.hasCanceledRunTimeMs()).isTrue()
    assertThat(loggedEvent.manifestMergerStats.hasFailedRunTimeMs()).isTrue()

    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.totalCount).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList).hasSize(1)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].start).isEqualTo(40)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].end).isEqualTo(42)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].samples).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.successRunTimeMs.binList[0].totalSamples).isEqualTo(2)

    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.totalCount).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.binList).hasSize(1)
    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.binList[0].start).isEqualTo(30)
    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.binList[0].end).isEqualTo(31)
    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.binList[0].samples).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.canceledRunTimeMs.binList[0].totalSamples).isEqualTo(2)

    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.totalCount).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.binList).hasSize(1)
    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.binList[0].start).isEqualTo(20)
    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.binList[0].end).isEqualTo(21)
    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.binList[0].samples).isEqualTo(2)
    assertThat(loggedEvent.manifestMergerStats.failedRunTimeMs.binList[0].totalSamples).isEqualTo(2)
  }

  @Test
  fun reportMergerStats_statsClearedAfterReporting() {
    ManifestMergerStatsTracker.recordManifestMergeRunTime(40.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(40.toDuration(DurationUnit.MILLISECONDS), SUCCESS)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(30.toDuration(DurationUnit.MILLISECONDS), CANCELED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(30.toDuration(DurationUnit.MILLISECONDS), CANCELED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), FAILED)
    ManifestMergerStatsTracker.recordManifestMergeRunTime(20.toDuration(DurationUnit.MILLISECONDS), FAILED)

    // Flush any remaining runnables on the event thread, since recording events happens asynchronously there.
    ApplicationManager.getApplication().invokeAndWait {}

    // First call logs results and is tested above.
    ManifestMergerStatsTracker.reportMergerStats()
    testUsageTrackerWriter.usages.clear()

    // Second call should log nothing.
    ManifestMergerStatsTracker.reportMergerStats()
    assertThat(testUsageTrackerWriter.usages).isEmpty()
  }
}
