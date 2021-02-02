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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class LiveLiteralsDiagnosticsTest {
  object FakeClock {
    var timeMs = 0L

    operator fun invoke() = timeMs
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project

  private fun LiveLiteralsDiagnosticsRead.assertCounts(success: Long, failed: Long) {
    assertEquals(success + failed, deploymentCount())
    assertEquals(success, successfulDeploymentCount())
    assertEquals(failed, failedDeploymentCount())
  }

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

    write.recordPushStarted("device1", "0")
    write.recordPushFinished("device2", "0", 0) // This push had never started so it will be ignored
    read.assertCounts(0, 0)
    assertTrue(read.lastDeployments().isEmpty())
    assertTrue(read.lastRecordedDevices().isEmpty())

    write.recordPushFinished("device1", "0", 0)
    write.recordPushFinished("device1", "0", 0) // This one should be ignored
    read.assertCounts(1, 0)
  }

  @Test
  fun `test parallel push`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstance(project)
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)

    write.recordPushStarted("device1", "0")
    write.recordPushStarted("device2", "0")
    read.assertCounts(0, 0)
    assertTrue(read.lastDeployments().isEmpty())
    assertTrue(read.lastRecordedDevices().isEmpty())

    write.recordPushFinished("device1", "0", 0)
    read.assertCounts(1, 0)
    write.recordPushFinished("device2", "0", 1)
    read.assertCounts(1, 1)

    assertEquals(2, read.lastDeploymentStats().records.count())
    assertEquals(1, read.lastDeploymentStatsForDevice("device1").records.count())
    assertEquals(1, read.lastDeploymentStatsForDevice("device2").records.count())
  }

  @Test
  fun `test time recording`() {
    val write = LiveLiteralsDiagnosticsManager.getWriteInstanceForTest(project) { FakeClock.timeMs }
    val read = LiveLiteralsDiagnosticsManager.getReadInstance(project)

    write.recordPushStarted("device1", "0")
    FakeClock.timeMs += 100
    write.recordPushFinished("device1", "0", 0)
    read.assertCounts(1, 0)
    assertEquals(100, read.lastDeployments().map { it.timeMs }.single())
    assertEquals(100, read.lastDeploymentStats().lastDeploymentTimesMs().single())
    assertEquals(100, read.lastDeploymentStatsForDevice("device1").lastDeploymentTimesMs().single())
    assertEquals(100, read.lastDeploymentStats().deployTime(50))

    write.recordPushStarted("device1", "1")
    FakeClock.timeMs += 10
    write.recordPushFinished("device1", "1", 0)
    assertEquals(2, read.lastDeployments().size)
    // Average should be 55 (100, 10)
    assertEquals(55, read.lastDeploymentStats().deployTime(50))
    // There is only one device so the stats should match
    assertEquals(read.lastDeploymentStatsForDevice("device1").deployTime(50), read.lastDeploymentStats().deployTime(50))
  }
}