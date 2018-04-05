/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.assistant

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


private val CONTENT_BORDER = JBUI.Borders.empty(5, 5, 7, 5)

/**
 * Base panel for Assistant that applies the correct margin the the provided [content]
 * and displays a title label.
 */
class AssistantPopupPanel(title: String, val content: JComponent) : JPanel(BorderLayout()) {

  private val titleLabel = JLabel(title).apply {
    border = JBUI.Borders.merge(
      JBUI.Borders.empty(8, 12, 8, 12),
      JBUI.Borders.customLine(com.android.tools.adtui.common.border, 0, 0, 1, 0), true)
    font = font.deriveFont(Font.BOLD, 10f)
  }

  private val contentWrapper = JPanel().apply {
    border = CONTENT_BORDER
    add(content)
  }

  init {
    add(titleLabel, BorderLayout.NORTH)
    add(contentWrapper)
  }
}
