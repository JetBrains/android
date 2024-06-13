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
package com.android.tools.idea.uibuilder.assistant

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private const val SIDE_PADDING = 12
private val CONTENT_BORDER = JBUI.Borders.empty(6, SIDE_PADDING, 12, SIDE_PADDING)

/**
 * Base panel for Assistant that applies the correct margin the the provided [content] and displays
 * a title label.
 */
open class AssistantPopupPanel
@JvmOverloads
constructor(title: String = "Design-time View Attributes", val content: JComponent? = null) :
  AdtSecondaryPanel() {

  private val titleLabel =
    JLabel(title, SwingConstants.LEADING).apply {
      border =
        JBUI.Borders.merge(
          JBUI.Borders.empty(8, SIDE_PADDING, 8, SIDE_PADDING),
          JBUI.Borders.customLine(com.android.tools.adtui.common.border, 0, 0, 1, 0),
          true,
        )
      font = font.deriveFont(JBUI.scaleFontSize(10f))
      isOpaque = false
    }

  private val contentWrapper =
    JPanel(BorderLayout()).apply {
      border = CONTENT_BORDER
      isOpaque = false
      if (content != null) {
        add(content)
      }
    }

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    isOpaque = false
    titleLabel.alignmentX = Component.LEFT_ALIGNMENT
    contentWrapper.alignmentX = Component.LEFT_ALIGNMENT
    add(titleLabel)
    add(contentWrapper)
    minimumSize = JBUI.size(250, 10)
  }

  fun addContent(content: JComponent) {
    contentWrapper.removeAll()
    contentWrapper.add(content)
  }

  final override fun add(comp: Component?): Component {
    return super.add(comp)
  }
}
