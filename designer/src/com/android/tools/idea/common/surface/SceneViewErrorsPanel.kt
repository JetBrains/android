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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.common.AdtUiUtils
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import javax.swing.border.LineBorder

/**
 * [JPanel] to be displayed when a given [SceneView] has render errors.
 *
 * The panel uses a [TabularLayout] with the following [TabularLayout.Constraint]s:
 *        ______________________________
 *  10px |                             |
 *       |_____________________________|
 *       |     |                 |     |
 *       |     |                 |     |
 *       |10 px|  panelContent   |10 px|
 *       |     |                 |     |
 *       |     |                 |     |
 *        ______________________________
 *  10px |                             |
 *       |_____________________________|
 */
class SceneViewErrorsPanel(private val isPanelVisible: () -> Boolean = { true }) : JPanel(TabularLayout("10px,*,10px", "10px,*,10px")) {

  private val size = JBUI.size(150, 100)

  private val label = JBLabel("<html>Some issues were found while trying to render this preview.</html>").apply { foreground = Gray._119 }

  init {
    add(label, TabularLayout.Constraint(1, 1))
    border = LineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1)
  }

  override fun getPreferredSize() = size

  override fun getMinimumSize() = size

  override fun isVisible() = isPanelVisible()
}