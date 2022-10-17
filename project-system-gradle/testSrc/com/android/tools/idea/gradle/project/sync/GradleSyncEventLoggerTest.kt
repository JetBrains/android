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

    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(0)
  }

  @Test
  fun whensSetupStarted() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    now = 2
    eventLogger.setupStarted()

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)

    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(2)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(-1)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(2)
  }

  @Test
  fun whensEnded() {
    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    now = 2
    eventLogger.setupStarted()
    now = 5
    eventLogger.syncEnded()

    val event = eventLogger.generateSyncEvent(projectRule.project, "/", AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)

    expect.that(event.gradleSyncStats.syncType).named("syncType").isEqualTo(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT)
    expect.that(event.gradleSyncStats.trigger).named("trigger").isEqualTo(GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED)
    expect.that(event.gradleSyncStats.gradleTimeMs).named("gradleTimeMs").isEqualTo(2)
    expect.that(event.gradleSyncStats.ideTimeMs).named("ideTimeMs").isEqualTo(3)
    expect.that(event.gradleSyncStats.totalTimeMs).named("totalTimeMs").isEqualTo(5)
  }
}