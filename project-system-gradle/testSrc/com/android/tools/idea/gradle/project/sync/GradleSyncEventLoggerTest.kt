/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Expect
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.PhaseResult
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.GRADLE_CONFIGURE_BUILD
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.GRADLE_CONFIGURE_ROOT_BUILD
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.PROJECT_SETUP
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.GRADLE_RUN_MAIN_TASKS
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.GRADLE_RUN_WORK
import com.google.wireless.android.sdk.stats.GradleSyncStats.GradleSyncPhaseData.SyncPhase.SYNC_TOTAL
import org.junit.Rule
import org.junit.Test

class GradleSyncEventLoggerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val expect: Expect = Expect.create()

  private var now: Long = 0

  private val eventLogger = GradleSyncEventLogger { now }

  @get:Rule
  val ignoreTests = IgnoreTestRule()

  @Test
  fun whenStarted() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)

    expect.that(event.kind).named("kind").isEqualTo(AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)
    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(0)
    expect.that(event.gradleSyncStats.gradleSyncPhasesDataList).named("gradleSyncPhasesDataList").isEmpty()
  }

  @Test
  fun whensSetupStarted() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    now = 2
    eventLogger.setupStarted()

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED)

    expect.that(event.kind).named("kind").isEqualTo(AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED)
    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(2)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(2)
    expect.that(event.gradleSyncStats.gradleSyncPhasesDataList).named("gradleSyncPhasesDataList").isEmpty()
  }

  @Test
  fun whensEnded() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)

    // Phases Events from Gradle
    eventLogger.syncPhaseStarted(GRADLE_CONFIGURE_ROOT_BUILD)
    eventLogger.syncPhaseStarted(GRADLE_CONFIGURE_BUILD)
    eventLogger.gradlePhaseFinished(GRADLE_CONFIGURE_BUILD, 1, 2, PhaseResult.SUCCESS)
    eventLogger.syncPhaseStarted(GRADLE_RUN_WORK)
    eventLogger.gradlePhaseFinished(GRADLE_RUN_WORK, 2, 3, PhaseResult.SUCCESS)
    eventLogger.gradlePhaseFinished(GRADLE_CONFIGURE_ROOT_BUILD, 1, 3, PhaseResult.SUCCESS)
    eventLogger.syncPhaseStarted(GRADLE_RUN_MAIN_TASKS)
    eventLogger.syncPhaseStarted(GRADLE_RUN_WORK)
    eventLogger.gradlePhaseFinished(GRADLE_RUN_WORK, 3, 4, PhaseResult.SUCCESS)
    eventLogger.gradlePhaseFinished(GRADLE_RUN_MAIN_TASKS, 3, 4, PhaseResult.SUCCESS)

    now = 4
    eventLogger.setupStarted()
    now = 5
    eventLogger.syncEnded(success = true)

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED)

    expect.that(event.kind).named("kind").isEqualTo(AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED)
    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(4)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(5)
    expect.that(event.gradleSyncStats.gradleSyncPhasesDataList).containsExactly(
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD, GRADLE_CONFIGURE_BUILD), 1, 2, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD, GRADLE_RUN_WORK), 2, 3, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD), 1, 3, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_RUN_MAIN_TASKS, GRADLE_RUN_WORK), 3, 4, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_RUN_MAIN_TASKS), 3, 4, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, PROJECT_SETUP), 4, 5, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL), 0, 5, PhaseResult.SUCCESS)
    )
  }

  @Test
  fun whensFailed() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)

    // Phases Events from Gradle
    eventLogger.syncPhaseStarted(GRADLE_CONFIGURE_ROOT_BUILD)
    eventLogger.syncPhaseStarted(GRADLE_CONFIGURE_BUILD)
    eventLogger.gradlePhaseFinished(GRADLE_CONFIGURE_BUILD, 1, 2, PhaseResult.SUCCESS)
    eventLogger.syncPhaseStarted(GRADLE_RUN_WORK)
    eventLogger.gradlePhaseFinished(GRADLE_RUN_WORK, 2, 3, PhaseResult.SUCCESS)
    eventLogger.gradlePhaseFinished(GRADLE_CONFIGURE_ROOT_BUILD, 1, 4, PhaseResult.FAILURE)

    now = 4
    eventLogger.syncEnded(success = false)

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)

    expect.that(event.kind).named("kind").isEqualTo(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)
    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(4)
    expect.that(event.gradleSyncStats.gradleSyncPhasesDataList).containsExactly(
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD, GRADLE_CONFIGURE_BUILD), 1, 2, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD, GRADLE_RUN_WORK), 2, 3, PhaseResult.SUCCESS),
      phase(listOf(SYNC_TOTAL, GRADLE_CONFIGURE_ROOT_BUILD), 1, 4, PhaseResult.FAILURE),
      phase(listOf(SYNC_TOTAL), 0, 4, PhaseResult.FAILURE)
    )
  }

  private fun phase(
    phase: List<SyncPhase>,
    startTimestamp: Long,
    endTimestamp: Long,
    status: PhaseResult
  ) = GradleSyncStats.GradleSyncPhaseData.newBuilder()
    .addAllPhaseStack(phase)
    .setPhaseStartTimestampMs(startTimestamp)
    .setPhaseEndTimestampMs(endTimestamp)
    .setPhaseResult(status)
    .build()
}