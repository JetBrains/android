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
package com.android.tools.idea.avdmanager

import com.android.tools.adtui.common.ColoredIconGenerator
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

private val INFO_ICON_COLORS = JBColor(0x6E6E6E, 0xAFB1B3)

/**
 * Small warning label to display that a system image is localized for China.
 */
class WearOsChinaLocalizedAlert : JPanel() {
  private val warningLabel = JBLabel("The selected image is a localized version of Wear OS for China",
                                     ColoredIconGenerator.generateColoredIcon(AllIcons.General.Information, INFO_ICON_COLORS),
                                     JLabel.LEADING).apply {
    isAllowAutoWrapping = true
  }

  init {
    layout = FlowLayout(FlowLayout.LEADING)
    border = BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Localized image"),
                                                BorderFactory.createEmptyBorder(0, 5, 3, 5))
    isOpaque = false
    warningLabel.isOpaque = false
    add(warningLabel)
  }
}