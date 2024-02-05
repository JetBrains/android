/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.liveedit.ui.DeviceGetter
import com.android.tools.idea.editors.liveedit.ui.LiveEditIssueNotificationAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.test.assertEquals

internal class LiveEditNotificationActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    (projectRule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }

  @Test
  fun `check simple states`() {
    val service = LiveEditService.getInstance(projectRule.project)
    val device: IDevice = MockitoKt.mock()

    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, projectRule.fixture.editor)
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .build()

    MockitoKt.whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    LiveEditIssueNotificationAction.deviceMap[projectRule.project] = object : DeviceGetter {
      override fun serial(dataContext: DataContext): String? {
        return "serial"
      }

      override fun device(dataContext: DataContext): IDevice? {
        return device
      }

      override fun devices(): List<IDevice> {
        return listOf(device)
      }
    }
    service.getDeployMonitor().liveEditDevices.addDevice(device, LiveEditStatus.UpToDate)

    // Event one. Pretending we are not the running device. We get full "Up-to-date"
    val action = LiveEditIssueNotificationAction()
    val event = TestActionEvent.createTestEvent(context)
    action.update(event)
    assertEquals("Up-to-date", event.presentation.text)

    // Event two. Pretending we are running device window. We should have the shorten status.
    val toolWindow: ToolWindow = MockitoKt.mock()
    MockitoKt.whenever(toolWindow.id).thenReturn(RUNNING_DEVICES_TOOL_WINDOW_ID)
    val context2 = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, projectRule.fixture.editor)
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
      .build()
    val action2 = LiveEditIssueNotificationAction()
    val event2 = TestActionEvent.createTestEvent(context2)
    action2.update(event2)
    assertEquals(event2.presentation.text, "")
  }

  @Test
  fun `check simple with fake device`() {
    val service = LiveEditService.getInstance(projectRule.project)
    fakeAdb.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    // Event two. Pretending we are running device window. We should have the shorten status.
    val file = projectRule.fixture.configureByText("A.kt", "")
    runBlocking(uiThread) { projectRule.fixture.openFileInEditor (file.virtualFile) }

    val toolWindow: ToolWindow = MockitoKt.mock()
    MockitoKt.whenever(toolWindow.id).thenReturn(RUNNING_DEVICES_TOOL_WINDOW_ID)
    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, projectRule.fixture.editor)
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .add(SERIAL_NUMBER_KEY, device.serialNumber)
      .build()

    service.getDeployMonitor().liveEditDevices.addDevice(device, LiveEditStatus.UpToDate)

    val action = LiveEditIssueNotificationAction()
    val event = TestActionEvent.createTestEvent(context)
    action.update(event)

    assertEquals("Up-to-date", event.presentation.text)
  }
}