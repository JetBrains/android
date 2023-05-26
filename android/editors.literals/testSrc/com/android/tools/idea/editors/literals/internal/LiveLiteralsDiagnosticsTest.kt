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
package com.android.tools.idea.editors.literals.internal

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.LiveLiteralsEvent
import com.intellij.openapi.project.Project
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals

class LiveLiteralsDiagnosticsTest {
  object FakeClock {
    val virtualScheduler = VirtualTimeScheduler()
    var timeMs = 0L
      set(value) {
        val increment = value - field
        if (increment > 0) {
          virtualScheduler.advanceBy(increment, TimeUnit.MILLISECONDS)
        }
        field = value
      }

    operator fun invoke() = timeMs
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  private val reportedEvents = mutableListOf<LiveLiteralsEvent>()

  private fun LiveLiteralsDiagnosticsRead.assertCounts(success: Long, failed: Long) {
    assertEquals(success + failed, deploymentCount())
    assertEquals(success, successfulDeploymentCount())
    assertEquals(failed, failedDeploymentCount())
  }

  private fun LiveLiteralsDiagnosticsManager.getWriteInstanceForTest(project: Project) =
    getWriteInstanceForTest(project, { FakeClock() }, { reportedEvents.add(it) }, FakeClock.virtualScheduler, 30, TimeUnit.MINUTES)

  @Test
  fun `test empty instance`() {
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)
    read.assertCounts(0, 0)
    assertTrue(read.lastDeployments().isEmpty())
    assertTrue(read.lastRecordedDevices().isEmpty())
  }

  @Test
  fun `test recording`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstance(project)
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)

    write.liveLiteralsMonitorStarted("device1", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    write.liveLiteralsMonitorStarted("device2", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    write.liveLiteralPushStarted("device1", "0")
    write.liveLiteralPushed("device2", "0", listOf()) // This push had never started so it will be ignored
    read.assertCounts(0, 0)
    assertTrue(read.lastDeployments().isEmpty())
    assertTrue(read.lastRecordedDevices().isEmpty())

    write.liveLiteralPushed("device1", "0", listOf())
    write.liveLiteralPushed("device1", "0", listOf()) // This one should be ignored
    read.assertCounts(1, 0)
  }

  @Test
  fun `test parallel push`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstance(project)
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)

    write.liveLiteralsMonitorStarted("device1", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    write.liveLiteralsMonitorStarted("device2", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    write.liveLiteralPushStarted("device1", "0")
    write.liveLiteralPushStarted("device2", "0")
    read.assertCounts(0, 0)
    assertTrue(read.lastDeployments().isEmpty())
    assertTrue(read.lastRecordedDevices().isEmpty())

    write.liveLiteralPushed("device1", "0", listOf())
    read.assertCounts(1, 0)
    write.liveLiteralPushed("device2", "0", listOf(LiveLiteralsMonitorHandler.Problem.error("")))
    read.assertCounts(1, 1)

    assertEquals(2, read.lastDeploymentStats().records.count())
    assertEquals(1, read.lastDeploymentStatsForDevice("device1").records.count())
    assertEquals(1, read.lastDeploymentStatsForDevice("device2").records.count())
  }

  @Test
  fun `test time recording`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstanceForTest(project)
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)

    write.liveLiteralsMonitorStarted("device1", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    write.liveLiteralPushStarted("device1", "0")
    FakeClock.timeMs += 100
    write.liveLiteralPushed("device1", "0", listOf())
    read.assertCounts(1, 0)
    assertEquals(100, read.lastDeployments().map { it.timeMs }.single())
    assertEquals(100, read.lastDeploymentStats().lastDeploymentTimesMs().single())
    assertEquals(100, read.lastDeploymentStatsForDevice("device1").lastDeploymentTimesMs().single())
    assertEquals(100, read.lastDeploymentStats().deployTime(50))

    write.liveLiteralPushStarted("device1", "1")
    FakeClock.timeMs += 10
    write.liveLiteralPushed("device1", "1", listOf())
    assertEquals(2, read.lastDeployments().size)
    // Average should be 55 (100, 10)
    assertEquals(55, read.lastDeploymentStats().deployTime(50))
    // There is only one device so the stats should match
    assertEquals(read.lastDeploymentStatsForDevice("device1").deployTime(50), read.lastDeploymentStats().deployTime(50))
  }

  @Test
  fun `test remote reporting`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstanceForTest(project)

    write.liveLiteralsMonitorStarted("device1", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    repeat(100) {
      write.liveLiteralPushStarted("device1", "a${it}")
      FakeClock.timeMs += Random.nextLong(50, 200)
      write.liveLiteralPushed("device1", "a${it}", if (it < 80) listOf() else listOf(LiveLiteralsMonitorHandler.Problem.error("Test")))
    }
    FakeClock.timeMs += TimeUnit.MINUTES.toMillis(40)
    assertEquals(2, reportedEvents.size)
    reportedEvents[0].let {
      assertEquals(LiveLiteralsEvent.LiveLiteralsEventType.START, it.eventType)
      assertEquals(LiveLiteralsEvent.LiveLiteralsDeviceType.PREVIEW, it.deviceType)
    }
    reportedEvents[1].let {
      assertEquals(LiveLiteralsEvent.LiveLiteralsEventType.DEPLOY_STATS, it.eventType)
      val stats = it.getDeployStats(0)
      assertEquals(80, stats.successfulDeployments)
      assertEquals(20, stats.failedDeployments)
      assertEquals(1, stats.devicesCount)
      assertEquals(LiveLiteralsEvent.LiveLiteralsDeviceType.PREVIEW, stats.deviceType)
    }

    // Check application level reporting
    reportedEvents.clear()
    val appWriting = LiveLiteralsDiagnosticsManager.getApplicationWriteInstanceForTest({ reportedEvents.add(it) },
                                                                                       MoreExecutors.directExecutor())
    appWriting.userChangedLiveLiteralsState(true)
    appWriting.userChangedLiveLiteralsState(false)
    appWriting.userChangedLiveLiteralsState(true)
    assertEquals(LiveLiteralsEvent.LiveLiteralsEventType.USER_ENABLE, reportedEvents[0].eventType)
    assertEquals(LiveLiteralsEvent.LiveLiteralsEventType.USER_DISABLE, reportedEvents[1].eventType)
    assertEquals(LiveLiteralsEvent.LiveLiteralsEventType.USER_ENABLE, reportedEvents[2].eventType)
  }
}