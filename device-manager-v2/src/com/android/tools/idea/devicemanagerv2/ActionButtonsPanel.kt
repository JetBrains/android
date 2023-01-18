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

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import javax.swing.BoxLayout

internal class ActionButtonsPanel(val project: Project, deviceRowData: DeviceRowData) :
  JBPanel<ActionButtonsPanel>() {
  val startStopButton = StartStopButton(deviceRowData.handle)
  val openDeviceExplorer = OpenDeviceExplorerButton(project, deviceRowData.handle)
  val editButton = EditButton(deviceRowData.handle)

  init {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    isOpaque = false
    add(startStopButton)
    add(openDeviceExplorer)
    add(editButton)
  }

  fun updateState(state: DeviceRowData) {
    openDeviceExplorer.updateState(state)
  }
}
