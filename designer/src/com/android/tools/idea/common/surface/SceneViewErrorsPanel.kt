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
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.border.LineBorder

private const val ERROR_LABEL_CONTENT = "Render problem"

/** Shows a Panel with an error message */
class SceneViewErrorsPanel(private val isPanelVisible: () -> Boolean = { true }) :
  JPanel(BorderLayout()) {

  private val size = JBUI.size(150, 35)
  private val label =
    JBLabel(ERROR_LABEL_CONTENT).apply {
      foreground = Gray._119
      minimumSize = size
      border = JBUI.Borders.empty(10)
    }

  init {
    add(label, BorderLayout.CENTER)
    border = LineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1)
  }

  override fun getPreferredSize() = size

  override fun getMinimumSize() = size

  override fun isVisible() = isPanelVisible()
}
