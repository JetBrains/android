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

import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class LiveLiteralsDeploymentReportServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project
  private lateinit var service: LiveLiteralsDeploymentReportService

  private fun Collection<Pair<String, LiveLiteralsMonitorHandler.Problem>>.asString() =
    map { "[${it.first}] ${it.second.severity}: ${it.second.content}" }
      .sorted()
      .joinToString("\n")

  @Before
  fun setup() {
    StudioFlags.COMPOSE_LIVE_LITERALS.override(true)
    service = LiveLiteralsDeploymentReportService.getInstance(project)
  }

  @Test
  fun `check deploy calls notify listener`() {
    var deployments = 0
    service.subscribe(projectRule.fixture.testRootDisposable, object: LiveLiteralsDeploymentReportService.Listener {
      override fun onMonitorStarted(deviceId: String) {}
      override fun onMonitorStopped(deviceId: String) {}

      override fun onLiveLiteralsPushed(deviceId: String) {
        deployments++
      }
    })

    assertFalse(service.hasProblems)
    assertFalse(service.hasActiveDevices)
    service.liveLiteralsMonitorStarted("DeviceA")
    assertTrue(service.hasActiveDevices)
    assertEquals(0, deployments)

    // Push to a different device should not trigger a deployment
    service.liveLiteralPushed("DeviceB")
    assertTrue(service.hasActiveDevices)
    assertEquals(0, deployments)

    service.liveLiteralPushed("DeviceA")
    assertTrue(service.hasActiveDevices)
    assertEquals(1, deployments)
  }

  @Test
  fun `check problems are recorded`() {
    assertFalse(service.hasProblems)
    assertFalse(service.hasActiveDevices)
    service.liveLiteralsMonitorStarted("DeviceA")
    // Finish the deployment successfully
    service.liveLiteralPushed("DeviceA")
    assertFalse(service.hasProblems)

    service.liveLiteralsMonitorStarted("DeviceA")
    service.liveLiteralPushed("DeviceA", listOf(
      LiveLiteralsMonitorHandler.Problem.info("Info"),
      LiveLiteralsMonitorHandler.Problem.warn("Warn"),
    ))
    assertTrue(service.hasProblems)

    assertEquals("""
      [DeviceA] INFO: Info
      [DeviceA] WARNING: Warn
    """.trimIndent(), service.problems.asString())

    service.liveLiteralsMonitorStarted("DeviceA")
    assertTrue(service.hasProblems)
    service.liveLiteralPushed("DeviceA")
    assertTrue(service.problems.isEmpty())
  }

  @Test
  fun `check multiple devices`() {
    var deployments = 0
    service.subscribe(projectRule.fixture.testRootDisposable, object: LiveLiteralsDeploymentReportService.Listener {
      override fun onMonitorStarted(deviceId: String) {}
      override fun onMonitorStopped(deviceId: String) {}

      override fun onLiveLiteralsPushed(deviceId: String) {
        deployments++
      }
    })
    service.liveLiteralPushed("DeviceB")
    assertEquals("The device was not active, no deployments expected", 0, deployments)

    service.liveLiteralsMonitorStarted("DeviceA")
    service.liveLiteralsMonitorStarted("DeviceB")
    service.liveLiteralPushed("DeviceB")
    assertTrue(service.hasActiveDevices)
    assertEquals(1, deployments)
    service.liveLiteralPushed("DeviceC") // Device is not active, will be ignored
    assertEquals(1, deployments)
    service.liveLiteralPushed("DeviceA")
    assertEquals(2, deployments)

    service.liveLiteralPushed("DeviceB", listOf(LiveLiteralsMonitorHandler.Problem.info("Test")))
    assertTrue(service.hasProblems)
    assertEquals(3, deployments)
    service.liveLiteralsMonitorStopped("DeviceB")
    assertFalse(service.hasProblems)

    // Device is not active, this should not be recorded
    service.liveLiteralPushed("DeviceB", listOf(LiveLiteralsMonitorHandler.Problem.info("Test")))
    assertFalse(service.hasProblems)
    service.liveLiteralPushed("DeviceA", listOf(LiveLiteralsMonitorHandler.Problem.info("Test")))
    assertTrue(service.hasProblems)
  }
}