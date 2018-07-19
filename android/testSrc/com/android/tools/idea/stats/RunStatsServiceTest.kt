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

import com.android.ddmlib.IDevice
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ArtifactDetail
import com.google.wireless.android.sdk.stats.StudioRunEvent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer
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
    myUsageTracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(myUsageTracker)
  }

  @After
  fun teardown() {
    myUsageTracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testNotifyRunStart() {
    myRunStatsService.notifyRunStarted(packageName, "Run", true, false, false)
    val usages = myUsageTracker.usages.filterNotNull()

    // verify called twice, once to start overall and other to track start of studio processing
    val runEvents = usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }
    Truth.assertThat(runEvents).hasSize(2)

    val totalStartEvent = runEvents[0].studioEvent.studioRunEvent
    Truth.assertThat(runEvents[0].studioEvent.projectId).isNotEmpty()
    Truth.assertThat(runEvents[0].studioEvent.rawProjectId).isEqualTo(packageName)
    Truth.assertThat(totalStartEvent.runType).isEqualTo(StudioRunEvent.RunType.RUN)
    Truth.assertThat(totalStartEvent.sectionType).isEqualTo(StudioRunEvent.SectionType.TOTAL)
    Truth.assertThat(totalStartEvent.eventType).isEqualTo(StudioRunEvent.EventType.START)
    Truth.assertThat(totalStartEvent.debuggable).isTrue()

    val studioStartEvent = runEvents[1].studioEvent.studioRunEvent
    Truth.assertThat(runEvents[1].studioEvent.projectId).isNotEmpty()
    Truth.assertThat(runEvents[1].studioEvent.rawProjectId).isEqualTo(packageName)
    Truth.assertThat(studioStartEvent.runType).isEqualTo(StudioRunEvent.RunType.RUN)
    Truth.assertThat(studioStartEvent.sectionType).isEqualTo(StudioRunEvent.SectionType.STUDIO)
    Truth.assertThat(studioStartEvent.eventType).isEqualTo(StudioRunEvent.EventType.START)
  }

  @Test
  fun testStudioProcessingTracking() {
    myRunStatsService.notifyRunStarted(packageName, "Run", false, false, true)
    var usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(2)

    myRunStatsService.notifyStudioSectionFinished(true, false, false)
    assertNotNull(myRunStatsService.myRun?.studioProcessFinishedTimestamp)
    usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(3)
    assertNotNull(
      usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT && it.studioEvent.studioRunEvent.durationMs > 0 })
    assertNotNull(
      usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT && !it.studioEvent.studioRunEvent.userSelectedTarget })
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

  // Test handling b/109945164
  @Test
  fun testStartedEmulatorWithoutStartEvent() {
    val duration = 500
    val currentTimeMillis = System.currentTimeMillis() - duration
    myRunStatsService.myRun = Run(UUID.randomUUID(), packageName, StudioRunEvent.RunType.DEBUG, currentTimeMillis)
    myRunStatsService.notifyEmulatorStarted(false)
    val usages = myUsageTracker.usages.filterNotNull()

    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(0)
  }

  @Test
  fun testDeployStarted() {
    myRunStatsService.myRun = Run(UUID.randomUUID(), packageName, StudioRunEvent.RunType.DEBUG, System.currentTimeMillis())
    val mockDevice = Mockito.mock(IDevice::class.java, Answer {
      if (String::class.java == it.method.returnType) {
        "This is my default answer for all methods that returns string"
      }
      else {
        Mockito.RETURNS_DEFAULTS.answer(it)
      }
    })
    val artifacts = listOf<ArtifactDetail>(ArtifactDetail.newBuilder().setSize(10).build(), ArtifactDetail.newBuilder().setSize(40).build())
    myRunStatsService.notifyDeployStarted(StudioRunEvent.DeployTask.SPLIT_APK_DEPLOY,
                                          mockDevice, artifacts, false, false, 0);
    val usages = myUsageTracker.usages.filterNotNull()
    Truth.assertThat(usages.filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }).hasSize(1)
    val deployStartedEvent = usages.find { it.studioEvent.kind == AndroidStudioEvent.EventKind.STUDIO_RUN_EVENT }?.studioEvent?.studioRunEvent
    Truth.assertThat(deployStartedEvent?.artifactCount).isEqualTo(2)
    Truth.assertThat(deployStartedEvent?.artifactDetailsList?.get(0)?.size).isEqualTo(10)
    Truth.assertThat(deployStartedEvent?.artifactDetailsList?.get(1)?.size).isEqualTo(40)
  }

}
