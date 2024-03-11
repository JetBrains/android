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

import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.tools.idea.deviceprovisioner.deviceHandle
import com.android.tools.idea.deviceprovisioner.deviceTemplate
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Component
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.android.refactoring.project

/** Updates the AnActionEvent's presentation from the given DeviceAction's presentation. */
fun AnActionEvent.updateFromDeviceAction(deviceActionProperty: DeviceHandle.() -> DeviceAction?) =
  updateFromDeviceAction(deviceHandle(), deviceActionProperty)

/** Updates the AnActionEvent's presentation from the given DeviceAction's presentation. */
fun AnActionEvent.updateFromDeviceTemplateAction(
  deviceActionProperty: DeviceTemplate.() -> DeviceAction?
) = updateFromDeviceAction(deviceTemplate(), deviceActionProperty)

private fun <T> AnActionEvent.updateFromDeviceAction(
  handleOrTemplate: T?,
  deviceActionProperty: T.() -> DeviceAction?,
) {
  when (val deviceAction = handleOrTemplate?.deviceActionProperty()) {
    null -> presentation.isEnabledAndVisible = false
    else -> {
      val actionPresentation = deviceAction.presentation.value
      presentation.isVisible = true
      presentation.isEnabled = actionPresentation.enabled
      presentation.text = actionPresentation.label
    }
  }
}

/**
 * Updates the presentation from the given action, except that it is also enabled if the
 * deactivation action is enabled. (This is for Delete and Wipe Data, which are enabled in the UI if
 * it's possible to stop the device in order to wipe / delete it.)
 */
fun AnActionEvent.updateFromDeviceActionOrDeactivateAction(
  deviceActionProperty: DeviceHandle.() -> DeviceAction?
) {
  val handle = deviceHandle()
  when (val deviceAction = handle?.deviceActionProperty()) {
    null -> presentation.isEnabledAndVisible = false
    else -> {
      presentation.isVisible = true
      presentation.isEnabled = deviceAction.isEnabled() || handle.deactivationAction.isEnabled()
      presentation.text = deviceAction.presentation.value.label
    }
  }
}

internal fun AnActionEvent.deviceRowData() = DEVICE_ROW_DATA_KEY.getData(dataContext)

internal fun AnActionEvent.deviceManagerPanel() = DEVICE_MANAGER_PANEL_KEY.getData(dataContext)

internal fun AnActionEvent.deviceManagerCoroutineScope(): CoroutineScope? =
  DEVICE_MANAGER_COROUTINE_SCOPE_KEY.getData(dataContext)

internal fun DeviceAction?.isEnabled() = this?.presentation?.value?.enabled == true

internal fun projectFromComponentContext(component: Component) =
  DataManager.getInstance().getDataContext(component).project
