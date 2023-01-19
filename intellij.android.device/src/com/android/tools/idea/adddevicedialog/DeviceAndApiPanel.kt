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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.device.Resolution
import com.android.tools.idea.grouplayout.GroupLayout.Companion.groupLayout
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import javax.swing.GroupLayout
import javax.swing.JList

internal class DeviceAndApiPanel internal constructor() : JBPanel<DeviceAndApiPanel>(null) {
  init {
    val nameLabel = JBLabel("Name")
    val nameTextField = JBTextField()

    val deviceDefinitionLabel = JBLabel("Device definition")
    val deviceDefinitionComboBox = initDeviceDefinitionComboBox()

    val apiLevelLabel = JBLabel("API level")
    val apiLevelComboBox = initApiLevelComboBox()

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

  private fun initDeviceDefinitionComboBox(): Component {
    val comboBox = ComboBox(arrayOf(Definition(name = "Pixel 4", size = 5.8, resolution = Resolution(1080, 2340), density = 440)))

    comboBox.renderer = object : ColoredListCellRenderer<Definition>() {
      override fun customizeCellRenderer(list: JList<out Definition>,
                                         definition: Definition,
                                         index: Int,
                                         selected: Boolean,
                                         focused: Boolean) {
        append("${definition.name} ")
        append("${definition.size}″, ${definition.resolution}, ${definition.density} dpi", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    return comboBox
  }

  private fun initApiLevelComboBox(): Component {
    val comboBox = ComboBox(arrayOf(AndroidVersion(VersionCodes.S)))

    comboBox.renderer = object : ColoredListCellRenderer<AndroidVersion>() {
      override fun customizeCellRenderer(list: JList<out AndroidVersion>,
                                         version: AndroidVersion,
                                         index: Int,
                                         selected: Boolean,
                                         focused: Boolean) {
        append("$version ")

        append("Android ${SdkVersionInfo.getVersionString(version.apiLevel)} ${SdkVersionInfo.getCodeName(version.featureLevel)}",
               SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }

    return comboBox
  }
}
