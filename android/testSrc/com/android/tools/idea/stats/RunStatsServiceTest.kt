/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.StudioRunEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RunStatsServiceTest {
  private val packageName = "test"
  private lateinit var myRunStatsService: RunStatsService
  private lateinit var myUsageTracker: TestUsageTracker

  @Before
  fun setup() {
    myRunStatsService = RunStatsServiceImpl()
    myUsageTracker = TestUsageTracker(AnalyticsSettings(), VirtualTimeScheduler())
    UsageTracker.setInstanceForTest(myUsageTracker)
  }

  @After
  fun teardown() {
    myUsageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testNotifyRunStart() {
    myRunStatsService.notifyRunStarted(packageName, "Run", false, false)
    val usages = myUsageTracker.usages.filterNotNull()

    // verify called twice, once to start overall and other to track start of studio processing
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(2)
  }

  @Test
  fun testStudioProcessingTracking() {
    myRunStatsService.notifyRunStarted(packageName, "Run", false, false)
    var usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(2)

    myRunStatsService.notifyStudioSectionFinished(true, false, false)
    assertNotNull(myRunStatsService.myRun?.studioProcessFinishedTimestamp)
    usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(3)
    assertNotNull(
      usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT && it.studioEvent.studioRunEvent.durationMs > 0 })
  }

  @Test
  fun testTotalDeployFinished() {
    val duration = 500
    val currentTimeMillis = System.currentTimeMillis() - duration
    myRunStatsService.myRun = Run(UUID.randomUUID(), packageName, StudioRunEvent.RunType.DEBUG, currentTimeMillis)

    myRunStatsService.notifyRunFinished(true)
    val usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(1)
    Truth.assertThat(
      usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }?.studioEvent?.studioRunEvent?.durationMs).isAtLeast(
      duration)
  }

  // Test RunStatsService handles the case of AVD being started outside of a deploy -> no logging
  @Test
  fun testStartingEmulatorOnly() {
    myRunStatsService.notifyEmulatorStarting()
    val usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(0)
    assertNull(myRunStatsService.myRun)
  }

}
