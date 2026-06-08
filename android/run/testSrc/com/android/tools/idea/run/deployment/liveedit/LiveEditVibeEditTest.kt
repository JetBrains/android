/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.tokens.FakeBuildSystemLiveEditServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class LiveEditVibeEditTest {
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())
  private lateinit var myProject: Project

  @get:Rule var projectRule = AndroidProjectRule.onDisk().withKotlin()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
    myProject = projectRule.project
    FakeBuildSystemLiveEditServices().register(projectRule.testRootDisposable)
    setUpComposeInProjectFixture(projectRule)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testVibeEditWithoutRunningDevices() {
    StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.override(true)
    try {
      LiveEditApplicationConfiguration.getInstance().leTriggerMode = LiveEditService.Companion.LiveEditTriggerMode.ON_HOTKEY
      val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
      val file = projectRule.createKtFile("src/A.kt", "fun foo() {}")
      try {
        monitor.onAgentTrigger(file.virtualFile.path, "do fun things with it.")
        fail("Exception should have been thrown.")
      } catch (e: LiveEditUpdateException) {
        assertTrue(e.message!!.contains("No running application available for Live Edit"))
      }
    } finally {
      StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.clearOverride()
    }
  }

  @Test
  fun testVibeEditDisabledByFlag() {
    StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.override(false)
    try {
      val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
      val file = projectRule.createKtFile("src/A.kt", "fun foo() {}")
      val device: IDevice = mock()
      whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
      monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }

      monitor.onAgentTrigger(file.virtualFile.path, "do fun things")
      fail("Exception should have been thrown.")
    } catch (e: LiveEditUpdateException) {
      assertTrue(e.message!!.contains("Vibe Edit agent is disabled"))
    } finally {
      StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.clearOverride()
    }
  }

  @Test
  fun testVibeEditOutsideProjectRoot() {
    StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.override(true)
    try {
      val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)

      val device: IDevice = mock()
      whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
      monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), emptyList()) { true }
      monitor.liveEditDevices.update(device, LiveEditStatus.UpToDate)

      val tempPath = java.nio.file.Files.createTempFile("outside", ".kt")
      try {
        monitor.onAgentTrigger(tempPath.toAbsolutePath().toString(), "do fun things")
        fail("Exception should have been thrown.")
      } catch (e: LiveEditUpdateException) {
        assertTrue(e.message!!.contains("is outside the project content root"))
      } finally {
        java.nio.file.Files.deleteIfExists(tempPath)
      }
    } finally {
      StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.clearOverride()
    }
  }

  @Test
  fun testVibeEditFileNotFound() {
    StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.override(true)
    try {
      val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
      val device: IDevice = mock()
      whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
      monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), emptyList()) { true }
      monitor.liveEditDevices.update(device, LiveEditStatus.UpToDate)

      monitor.onAgentTrigger("/non/existent/file.kt", "do fun things")
      fail("Exception should have been thrown.")
    } catch (e: LiveEditUpdateException) {
      assertTrue(e.message!!.contains("not found in local file system"))
    } finally {
      StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.clearOverride()
    }
  }

  @Test
  fun testVibeEditSuccessGates() {
    StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.override(true)
    try {
      val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
      val file = projectRule.createKtFile("src/A.kt", "fun foo() {}")

      val device: IDevice = mock()
      whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
      monitor.notifyAppDeploy(TestApplicationProjectContext("app"), device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }
      monitor.liveEditDevices.update(device, LiveEditStatus.UpToDate)

      // Since we don't have VibeTransformerProvider registered in OSS tests,
      // compiling a vibe edit will trigger "No extension for: ...", which is caught and
      // reported in the device's Live Edit status.
      monitor.onAgentTrigger(file.virtualFile.path, "do fun things")

      val status = monitor.status(device)
      assertTrue(status.description.contains("No extension for: com.android.tools.idea.run.deployment.liveedit.vibeTransformerProvider"))
    } finally {
      StudioFlags.STUDIOBOT_DEPLOY_VIBE_EDIT_AGENT.clearOverride()
    }
  }
}
