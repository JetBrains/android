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

    val layout = GroupLayout(this)
    val maxSize = JBUIScale.scale(Short.MAX_VALUE.toInt())

    val horizontalGroup = layout.createParallelGroup()
      .addComponent(nameLabel)
      .addComponent(nameTextField)
      .addComponent(deviceDefinitionLabel)
      .addComponent(deviceDefinitionComboBox)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(apiLevelLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, maxSize)
                  .addComponent(servicesLabel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, maxSize))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(apiLevelComboBox)
                  .addComponent(servicesComboBox))
      .addComponent(abiLabel)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(abiComboBox)
                  .addContainerGap(abiComboBox.preferredSize.width, maxSize))

    val verticalGroup = layout.createSequentialGroup()
      .addComponent(nameLabel)
      .addComponent(nameTextField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addComponent(deviceDefinitionLabel)
      .addComponent(deviceDefinitionComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addGroup(layout.createParallelGroup()
                  .addComponent(apiLevelLabel)
                  .addComponent(servicesLabel))
      .addGroup(layout.createParallelGroup()
                  .addComponent(apiLevelComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  .addComponent(servicesComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addComponent(abiLabel)
      .addComponent(abiComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    setLayout(layout)
  }
}
