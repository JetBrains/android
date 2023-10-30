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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.AdtUiUtils
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.border.LineBorder

private const val ERROR_LABEL_CONTENT = "Render problem"
private val TRANSLUCENT_BACKGROUND_COLOR = Gray._220.withAlpha(200)
private val DEFAULT_BORDER = LineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1)

/** Shows a Panel with an error message */
class SceneViewErrorsPanel(val styleProvider: () -> Style = { Style.SOLID }) :
  JPanel(BorderLayout()) {

  /** The style applied to the panel */
  enum class Style {
    /** The panel is not visible */
    HIDDEN,
    /** The panel is showing and is opaque */
    SOLID,
    /** The panel is showing and is translucent */
    TRANSLUCENT
  }

  private val size = JBUI.size(150, 35)
  private val label =
    JBLabel(ERROR_LABEL_CONTENT).apply {
      foreground = Gray._119
      minimumSize = size
      border = JBUI.Borders.empty(10)
    }
  private val boldFont = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  private var lastStyle: Style? = null

  init {
    add(label, BorderLayout.CENTER)
    updateStyles()
  }

  override fun getPreferredSize() = size

  override fun getMinimumSize() = size

  /** Updates the look and feel of the panel with the style and returns the current set style. */
  private fun updateStyles(): Style {
    val newStyle = styleProvider()
    if (newStyle != lastStyle) {
      when (newStyle) {
        Style.SOLID -> {
          label.foreground = Gray._119
          border = DEFAULT_BORDER
          label.font = UIUtil.getLabelFont()
          background = UIUtil.getPanelBackground()
        }
        Style.TRANSLUCENT -> {
          label.foreground = Gray._15
          border = JBUI.Borders.empty()
          label.font = boldFont
          background = TRANSLUCENT_BACKGROUND_COLOR
        }
        Style.HIDDEN -> {}
      }
      lastStyle = newStyle
    }
    return newStyle
  }

  override fun isVisible(): Boolean {
    return updateStyles() != Style.HIDDEN
  }
}
