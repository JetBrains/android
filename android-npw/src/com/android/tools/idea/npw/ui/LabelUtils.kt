/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.ui

import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import icons.StudioIcons.Common.WARNING
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

fun createWarningLabel(labelText: String): JPanel {
  return JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)

    val darkBackground = 0x3D3223
    val darkBorder = 0xD6AE58
    val lightBackground = 0xFFFAEB
    val lightBorder = 0xC27D04

    background = JBColor(lightBackground, darkBackground)
    border =
      JBUI.Borders.compound(
        RoundedLineBorder(JBColor(lightBorder, darkBorder), JBUIScale.scale(8), 1),
        empty(JBUIScale.scale(8)),
      )

    val iconLabel = JLabel(WARNING).apply { alignmentY = Component.TOP_ALIGNMENT }
    add(iconLabel)

    add(Box.createHorizontalStrut(JBUIScale.scale(8)))

    val warningTextLabel = JLabel(labelText).apply { alignmentY = Component.TOP_ALIGNMENT }
    add(warningTextLabel)
  }
}
