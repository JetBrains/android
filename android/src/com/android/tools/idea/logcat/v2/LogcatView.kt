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
package com.android.tools.idea.logcat.v2

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import icons.StudioIcons

internal class LogcatView(project: Project, toolWindow: ToolWindow, bridge: AndroidDebugBridge)
  : AndroidDebugBridge.IDeviceChangeListener, Disposable {

  private val contentManager = toolWindow.contentManager

  init {
    Disposer.register(project, this)
    AndroidDebugBridge.addDeviceChangeListener(this)
    bridge.devices.filter { findDeviceContent(it) == null }.forEach { addDeviceContent(it) }
  }

  override fun deviceConnected(device: IDevice) {
    runInEdt {
      findDeviceContent(device)?.let {
        it.isCloseable = false
      } ?: addDeviceContent(device)
    }
  }

  override fun deviceDisconnected(device: IDevice) {
    findDeviceContent(device)?.let { it.isCloseable = true }
  }

  override fun deviceChanged(device: IDevice, changeMask: Int) {}

  private fun findDeviceContent(device: IDevice): Content? =
    contentManager.contents.find { (it.component as? DeviceLogcatPanel)?.device?.serialNumber == device.serialNumber }

  private fun addDeviceContent(device: IDevice) {
    val content = contentManager.factory.createContent(DeviceLogcatPanel(device), device.name, /* isLockable= */ false)
    content.isCloseable = false
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    content.icon = StudioIcons.Shell.ToolWindows.LOGCAT
    contentManager.addContent(content)
  }

  override fun dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this)
  }
}