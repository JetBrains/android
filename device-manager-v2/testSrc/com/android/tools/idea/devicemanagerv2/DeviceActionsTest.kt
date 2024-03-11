/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.deviceprovisioner.DEVICE_TEMPLATE_KEY
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class DeviceActionsTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun updateFromDeviceAction() = runBlocking {
    val handle = FakeDeviceHandle(this)
    handle.deactivationAction.presentation.update { it.copy(enabled = false, label = "Stop!") }

    val event = actionEvent(dataContext(device = handle))
    event.updateFromDeviceAction(DeviceHandle::deactivationAction)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text).isEqualTo("Stop!")

    handle.deactivationAction.presentation.update { it.copy(enabled = true) }
    event.updateFromDeviceAction(DeviceHandle::deactivationAction)

    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun updateFromDeviceActionOrDeactivateAction() = runBlocking {
    val handle = FakeDeviceHandle(this)
    handle.repairDeviceAction.presentation.update { it.copy(enabled = false, label = "Fix it") }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }

    val event = actionEvent(dataContext(device = handle))
    event.updateFromDeviceActionOrDeactivateAction(DeviceHandle::repairDeviceAction)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.text).isEqualTo("Fix it")

    handle.deactivationAction.presentation.update { it.copy(enabled = true) }
    event.updateFromDeviceActionOrDeactivateAction(DeviceHandle::repairDeviceAction)

    assertThat(event.presentation.isEnabled).isTrue()

    handle.repairDeviceAction.presentation.update { it.copy(enabled = true) }
    handle.deactivationAction.presentation.update { it.copy(enabled = false) }
    event.updateFromDeviceActionOrDeactivateAction(DeviceHandle::repairDeviceAction)

    assertThat(event.presentation.isEnabled).isTrue()
  }
}

internal fun actionEvent(dataContext: DataContext) =
  AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

internal fun dataContext(
  deviceManager: DeviceManagerPanel? = null,
  deviceRowData: DeviceRowData? = null,
  device: DeviceHandle? = deviceRowData?.handle,
  deviceTemplate: DeviceTemplate? = deviceRowData?.template,
  coroutineScope: CoroutineScope? = deviceManager?.panelScope,
) = DataContext {
  when {
    DEVICE_HANDLE_KEY.`is`(it) -> device
    DEVICE_TEMPLATE_KEY.`is`(it) -> deviceTemplate
    DEVICE_ROW_DATA_KEY.`is`(it) -> deviceRowData
    DEVICE_MANAGER_PANEL_KEY.`is`(it) -> deviceManager
    DEVICE_MANAGER_COROUTINE_SCOPE_KEY.`is`(it) -> coroutineScope
    else -> null
  }
}
