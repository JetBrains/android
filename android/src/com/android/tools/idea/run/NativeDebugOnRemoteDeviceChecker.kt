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

package com.android.tools.idea.run

import com.android.adblib.serialNumber
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.execution.common.debug.AndroidDebugger
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder

const val PROPERTY_KEY = "nativeDebuggerOnRemoteDeviceWarning"
const val DEFAULT_VALUE = true

class NativeDebugOnRemoteDeviceChecker(project: Project) {
  val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
  private val propertiesComponent = PropertiesComponent.getInstance(project)

  private var showWarning: Boolean
    get() = propertiesComponent.getBoolean(PROPERTY_KEY, DEFAULT_VALUE)
    set(value) {
      propertiesComponent.setValue(PROPERTY_KEY, value, DEFAULT_VALUE)
    }

  fun showWarningIfNeeded(debugger: AndroidDebugger<*>, devices: List<AndroidDevice>): Boolean {
    if (showWarning && debugger.isNativeDebugger() && devices.any { it.isRemote }) {
      return showWarning()
    }
    return true
  }

  fun showWarningIfNeeded(debugger: AndroidDebugger<*>, deviceSerial: String): Boolean {
    if (showWarning && debugger.isNativeDebugger() && deviceProvisioner.isRemote(deviceSerial)) {
      return showWarning()
    }
    return true
  }

  private fun showWarning(): Boolean {
    return MessageDialogBuilder.yesNo(
      title = "Warning",
      message = "You are about to attach a native debugger to a remote device. This is not recommended and may result in poor performance. Are you sure you want to do this?",
    ).doNotAsk(object : DoNotAskOption.Adapter() {
      override fun getDoNotShowMessage() = "Do not ask me next time"

      override fun isSelectedByDefault() = false

      override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
        showWarning = !isSelected
      }
    }).guessWindowAndAsk()
  }
}

private fun DeviceHandle.isRemote() = state.properties.isRemote == true

private fun AndroidDebugger<*>.isNativeDebugger() = this !is AndroidJavaDebugger

private fun DeviceProvisioner.isRemote(serialNumber: String) =
  devices.value.find { it.state.connectedDevice?.serialNumber == serialNumber }?.isRemote() == true
