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

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants


private const val LEFT_PADDING = 5
private val CONTENT_BORDER = JBUI.Borders.empty(5, LEFT_PADDING, 7, 5)

/**
 * Base panel for Assistant that applies the correct margin the the provided [content]
 * and displays a title label.
 */
open class AssistantPopupPanel @JvmOverloads constructor(title: String = "DESIGN-TIME ATTRIBUTES", val content: JComponent? = null) : JPanel(
  VerticalFlowLayout()) {

  private val titleLabel = JLabel(title, SwingConstants.LEADING).apply {
    border = JBUI.Borders.merge(
      JBUI.Borders.empty(8, LEFT_PADDING, 8, 12),
      JBUI.Borders.customLine(com.android.tools.adtui.common.border, 0, 0, 1, 0), true)
    font = font.deriveFont(Font.BOLD, 10f)
    isOpaque = false
  }

  private val contentWrapper = JPanel(BorderLayout()).apply {
    border = CONTENT_BORDER
    isOpaque = false
    if (content != null) {
      add(content)
    }
  }

  init {
    add(titleLabel)
    add(contentWrapper)
    background = UIUtil.getListBackground()
  }

  fun addContent(content: JComponent) {
    contentWrapper.removeAll()
    contentWrapper.add(content)
  }

  final override fun add(comp: Component?): Component {
    return super.add(comp)
  }
}
