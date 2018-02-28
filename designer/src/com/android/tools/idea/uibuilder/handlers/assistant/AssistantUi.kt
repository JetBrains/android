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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.tools.adtui.common.AdtUiUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

internal val MAIN_PANEL_BORDER = JBUI.Borders.empty(10)
internal val MAIN_PANEL_BACKGROUND = UIUtil.getListBackground()

fun setAssistantFont(component: JComponent, foregroundColor: Color? = null) = component.apply {
  font = JBUI.Fonts.smallFont()
  if (foregroundColor != null) {
    foreground = foregroundColor
  }
}

fun assistantLabel(text: String, alignment: Int = SwingConstants.LEADING): JLabel = JLabel(text, alignment).apply {
  setAssistantFont(this, AdtUiUtils.DEFAULT_FONT_COLOR)
  isOpaque = false
}
