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
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder

const val PROPERTY_KEY = "nativeDebuggerOnRemoteDeviceWarning"
const val DEFAULT_VALUE = true

class NativeDebugOnRemoteDeviceChecker(private val project: Project) {
  val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
  private val propertiesComponent = PropertiesComponent.getInstance(project)

  private var showWarning: Boolean
    get() = propertiesComponent.getBoolean(PROPERTY_KEY, DEFAULT_VALUE)
    set(value) {
      propertiesComponent.setValue(PROPERTY_KEY, value, DEFAULT_VALUE)
    }

  fun showWarningIfNeeded(debugger: AndroidDebugger<*>, devices: List<AndroidDevice>): Boolean {
    if (debugger.isNativeDebugger() && devices.any { it.isRemote }) {
      return showWarningIfNeeded()
    }
    return true
  }

  fun showWarningIfNeeded(debugger: AndroidDebugger<*>, deviceSerial: String): Boolean {
    if (debugger.isNativeDebugger() && deviceProvisioner.isRemote(deviceSerial)) {
      return showWarningIfNeeded()
    }
    return true
  }

  private fun showWarningIfNeeded(): Boolean {
    val proceed = if (showWarning) {
      MessageDialogBuilder.yesNo(
        title = "Poor debugger performance",
        message = "Using a Native debugger with a remote device may result in very poor debugger performance. Do you want to proceed?",
      ).doNotAsk(object : DoNotAskOption.Adapter() {
        override fun getDoNotShowMessage() = "Don't show me again"

        override fun isSelectedByDefault() = false

        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          showWarning = !isSelected
        }
      }).guessWindowAndAsk()
    }
    else {
      true
    }
    if (proceed) {
      val notification =
        Notification(
          "Android",
          "Poor debugger performance",
          "Debugging native code with remote devices may result in very poor performance.",
          NotificationType.WARNING,
        )
      Notifications.Bus.notify(notification, project)
    }
    return proceed
  }
}

private fun DeviceHandle.isRemote() = state.properties.isRemote == true

private fun AndroidDebugger<*>.isNativeDebugger() = this !is AndroidJavaDebugger

private fun DeviceProvisioner.isRemote(serialNumber: String) =
  devices.value.find { it.state.connectedDevice?.serialNumber == serialNumber }?.isRemote() == true
