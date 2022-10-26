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
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import javax.swing.GroupLayout
import javax.swing.JButton

internal class AdditionalSettingsPanel internal constructor() : JBPanel<AdditionalSettingsPanel>(null) {
  init {
    val sdkExtensionLevelLabel = JBLabel("SDK extension level")
    val sdkExtensionLevelComboBox = ComboBox<Any>()

    val deviceSkinLabel = JBLabel("Device skin")
    val deviceSkinComboBox = ComboBox<Any>()
    val importButton = JButton("Import")

    val cameraSeparator = TitledSeparator("Camera")

    val frontLabel = JBLabel("Front")
    val frontComboBox = ComboBox<Any>()

    val rearLabel = JBLabel("Rear")
    val rearComboBox = ComboBox<Any>()

    val layout = GroupLayout(this)

    val horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addComponent(sdkExtensionLevelLabel)
                  .addComponent(sdkExtensionLevelComboBox))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(deviceSkinLabel)
                  .addComponent(deviceSkinComboBox)
                  .addComponent(importButton))
      .addComponent(cameraSeparator)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(frontLabel)
                  .addComponent(frontComboBox))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(rearLabel)
                  .addComponent(rearComboBox))

    val verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup()
                  .addComponent(sdkExtensionLevelLabel)
                  .addComponent(sdkExtensionLevelComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addGroup(layout.createParallelGroup()
                  .addComponent(deviceSkinLabel)
                  .addComponent(deviceSkinComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                  .addComponent(importButton))
      .addComponent(cameraSeparator, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addGroup(layout.createParallelGroup()
                  .addComponent(frontLabel)
                  .addComponent(frontComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addGroup(layout.createParallelGroup()
                  .addComponent(rearLabel)
                  .addComponent(rearComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    setLayout(layout)
  }
}
