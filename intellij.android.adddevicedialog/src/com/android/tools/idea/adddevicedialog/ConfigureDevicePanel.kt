/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.tools.idea.grouplayout.GroupLayout.Companion.groupLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTabbedPane
import java.awt.Component

internal class ConfigureDevicePanel internal constructor() : JBPanel<ConfigureDevicePanel>(null) {
  init {
    val configureDeviceLabel = JBLabel("Configure device")
    val addDeviceToDeviceManagerLabel = JBLabel("Add a device to the device manager")
    val tabbedPane = tabbedPane()

    layout = groupLayout(this) {
      horizontalGroup {
        parallelGroup {
          component(configureDeviceLabel)
          component(addDeviceToDeviceManagerLabel)
          component(tabbedPane)
        }
      }

      verticalGroup {
        sequentialGroup {
          component(configureDeviceLabel)
          component(addDeviceToDeviceManagerLabel)
          component(tabbedPane)
        }
      }
    }
  }

  private fun tabbedPane(): Component {
    val tabbedPane = JBTabbedPane()

    tabbedPane.addTab("Device and API", DeviceAndApiPanel())
    tabbedPane.addTab("Additional settings", AdditionalSettingsPanel())

    return tabbedPane
  }
}
