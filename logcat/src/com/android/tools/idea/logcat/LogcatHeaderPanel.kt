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
package com.android.tools.idea.logcat

import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.DevicePanel
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.FlowLayout.LEFT
import javax.swing.JPanel

/**
 * A header for the Logcat panel.
 */
internal class LogcatHeaderPanel(project: Project, deviceContext: DeviceContext) : JPanel(FlowLayout(LEFT)) {
  private val deviceComboBox: Component

  init {
    // TODO(aalbert): DevicePanel uses the project as a disposable parent. This doesn't work well with multiple tabs/splitters where we
    //  have an instance per tab/split and would like to be disposed when the container closes.
    //  It's not yet clear if we will and up using DevicePanel or not, so will not make changes to it just yet.
    val devicePanel = DevicePanel(project, deviceContext)
    deviceComboBox = devicePanel.deviceComboBox
    deviceComboBox.preferredSize = Dimension(JBUI.scale(300), deviceComboBox.minimumSize.height)
    add(deviceComboBox)
  }
}