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
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.editors.liveedit.ui.DeviceGetter
import com.android.tools.idea.editors.liveedit.ui.LiveEditDeviceMap
import com.android.tools.idea.editors.liveedit.ui.LiveEditIssueNotificationAction
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.TestActionEvent
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [LiveEditIssueNotificationAction]. */
internal class LiveEditIssueNotificationActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fakeAdbRule = FakeAdbServerAdbLibRule()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdbRule)!!

  @Before
  fun setUp() {
    (projectRule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
  }

  @Test
  fun `check simple states`() {
    val service = LiveEditService.getInstance(projectRule.project)
    val device: IDevice = mock()

    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, projectRule.fixture.editor)
      .add(CommonDataKeys.PROJECT, projectRule.project)
      .build()

    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    LiveEditDeviceMap.deviceMap[projectRule.project] = object : DeviceGetter {
      override fun serial(dataContext: DataContext): String {
        return "serial"
      }

      override fun device(dataContext: DataContext): IDevice {
        return device
      }

      override fun devices(): List<IDevice> {
        return listOf(device)
      }
    }
    service.getDeployMonitor().liveEditDevices.addDevice(device, LiveEditStatus.UpToDate)

    val action = LiveEditIssueNotificationAction()
    val event = TestActionEvent.createTestEvent(context)
    action.update(event)
    assertEquals("Up-to-date", event.presentation.text)
  }

  @Test
  fun `check simple with fake device`() {
    val service = LiveEditService.getInstance(projectRule.project)
    fakeAdbRule.connectDevice(
      deviceId = "device_id",
      manufacturer = "mfg",
      deviceModel = "model",
      release = "10.0.0",
      sdk = AndroidApiLevel(30),
      hostConnectionType = DeviceState.HostConnectionType.USB)
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    // Event two. Pretending we are running device window. We should have the shorten status.
    val file = projectRule.fixture.configureByText("A.kt", "")
    runBlocking(uiThread) { projectRule.fixture.openFileInEditor (file.virtualFile) }

    val toolWindow: ToolWindow = mock()
    whenever(toolWindow.id).thenReturn(RUNNING_DEVICES_TOOL_WINDOW_ID)
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