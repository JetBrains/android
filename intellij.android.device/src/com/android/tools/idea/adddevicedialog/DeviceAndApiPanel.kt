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
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import javax.swing.GroupLayout

internal class DeviceAndApiPanel internal constructor() : JBPanel<DeviceAndApiPanel>(null) {
  init {
    val nameLabel = JBLabel("Name")
    val nameTextField = JBTextField()

    val deviceDefinitionLabel = JBLabel("Device definition")
    val deviceDefinitionComboBox = ComboBox<Any>()

    val apiLevelLabel = JBLabel("API level")
    val apiLevelComboBox = ComboBox<Any>()

    val servicesLabel = JBLabel("Services")
    val servicesComboBox = ComboBox<Any>()

    val abiLabel = JBLabel("ABI")
    val abiComboBox = ComboBox<Any>()

    val max = JBUIScale.scale(Short.MAX_VALUE.toInt())

    layout = groupLayout(this) {
      horizontalGroup {
        parallelGroup {
          component(nameLabel)
          component(nameTextField)
          component(deviceDefinitionLabel)
          component(deviceDefinitionComboBox)

          sequentialGroup {
            component(apiLevelLabel, max = max)
            component(servicesLabel, max = max)
          }

          sequentialGroup {
            component(apiLevelComboBox)
            component(servicesComboBox)
          }

          component(abiLabel)

          sequentialGroup {
            component(abiComboBox)
            containerGap(abiComboBox.preferredSize.width, max)
          }
        }
      }

      verticalGroup {
        sequentialGroup {
          component(nameLabel)
          component(nameTextField, max = GroupLayout.PREFERRED_SIZE)
          component(deviceDefinitionLabel)
          component(deviceDefinitionComboBox, max = GroupLayout.PREFERRED_SIZE)

          parallelGroup {
            component(apiLevelLabel)
            component(servicesLabel)
          }

          parallelGroup {
            component(apiLevelComboBox, max = GroupLayout.PREFERRED_SIZE)
            component(servicesComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          component(abiLabel)
          component(abiComboBox, max = GroupLayout.PREFERRED_SIZE)
        }
      }
    }
  }
}
