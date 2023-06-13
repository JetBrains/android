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
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.intellij.openapi.actionSystem.AnActionEvent

fun AnActionEvent.updateFromDeviceAction(deviceActionProperty: DeviceHandle.() -> DeviceAction?) {
  val handle = deviceHandle()
  when (val deviceAction = handle?.deviceActionProperty()) {
    null -> presentation.isEnabledAndVisible = false
    else -> {
      val actionPresentation = deviceAction.presentation.value
      presentation.isVisible = true
      presentation.isEnabled = actionPresentation.enabled
      presentation.text = actionPresentation.label
    }
  }
}

fun AnActionEvent.deviceHandle() = DEVICE_HANDLE_KEY.getData(dataContext)

internal fun AnActionEvent.deviceRowData() = DEVICE_ROW_DATA_KEY.getData(dataContext)

internal fun AnActionEvent.deviceManagerPanel() = DEVICE_MANAGER_PANEL_KEY.getData(dataContext)
