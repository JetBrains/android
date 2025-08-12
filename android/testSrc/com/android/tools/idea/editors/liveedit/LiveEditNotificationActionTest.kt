/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.flags.junit.FlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.editors.liveedit.ui.DeviceGetter
import com.android.tools.idea.editors.liveedit.ui.LiveEditDeviceMap
import com.android.tools.idea.editors.liveedit.ui.LiveEditNotificationAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.override
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Ignore("IDEA-369983")
/** Tests for [LiveEditNotificationAction]. */
internal class LiveEditNotificationActionTest {

  private val projectRule = AndroidProjectRule.inMemory()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdb, FlagRule(StudioFlags.LIVE_EDIT_COMPACT_STATUS_BUTTON, true))

  private val project
    get() = projectRule.project
  private val fixture
    get() = projectRule.fixture
  private val testRootDisposable
    get() = projectRule.testRootDisposable

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    LiveEditApplicationConfiguration.getInstance()::leTriggerMode.override(
      LiveEditService.Companion.LiveEditTriggerMode.AUTOMATIC, testRootDisposable)
    LiveEditDeviceMap.deviceMap.clear()
    Disposer.register(testRootDisposable) { LiveEditDeviceMap.unregisterProject(project) }
  }

  @Test
  fun `check simple states`() {
    val service = LiveEditService.getInstance(project)
    val device = mock<IDevice>()

    whenever(device.version).thenReturn(AndroidVersion(30, 0))
    LiveEditDeviceMap.deviceMap[project] = object : DeviceGetter {
      override fun serial(dataContext: DataContext): String = "serial"

      override fun device(dataContext: DataContext): IDevice = device

      override fun devices(): List<IDevice> = listOf(device)
    }
    service.getDeployMonitor().liveEditDevices.addDevice(device, LiveEditStatus.UpToDate)

    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .build()
    val action = LiveEditNotificationAction()
    val event = TestActionEvent.createTestEvent(context)
    action.update(event)
    assertThat(event.presentation.icon.toString()).isEqualTo("general/inspectionsOK.svg")
    assertThat(event.presentation.text).isNull()
    assertThat(event.presentation.description).isEqualTo(
      "App is up-to-date. Code changes will be automatically applied to the running app.")
  }

  @Test
  fun `check simple with fake device`() {
    val service = LiveEditService.getInstance(project)
    fakeAdb.connectAndWaitForDevice()
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val file = fixture.configureByText("A.kt", "")
    runBlocking(Dispatchers.EDT) { fixture.openFileInEditor(file.virtualFile) }

    val context = SimpleDataContext.builder()
      .add(CommonDataKeys.EDITOR, fixture.editor)
      .add(CommonDataKeys.PROJECT, project)
      .add(SERIAL_NUMBER_KEY, device.serialNumber)
      .build()

    service.getDeployMonitor().liveEditDevices.addDevice(device, LiveEditStatus.UpToDate)

    val action = LiveEditNotificationAction()
    val event = TestActionEvent.createTestEvent(context)
    action.update(event)

    assertThat(event.presentation.icon.toString()).isEqualTo("general/inspectionsOK.svg")
    assertThat(event.presentation.text).isNull()
    assertThat(event.presentation.description).isEqualTo(
      "App is up-to-date. Code changes will be automatically applied to the running app.")
  }
}