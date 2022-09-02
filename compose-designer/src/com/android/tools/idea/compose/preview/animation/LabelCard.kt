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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import javax.swing.border.MatteBorder

/** [Card] containing only animation label. */
class LabelCard(override val state: ElementState) : Card, JPanel(TabularLayout("*", "30px")) {

  // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
  // ⎹    transitionName                          ⎹ ⬅ component
  //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅

  private val firstRow =
    JPanel(TabularLayout("30px,*,Fit", "30px")).apply {
      border = JBUI.Borders.empty(0, 0, 0, 8)
      add(JBLabel(state.title ?: "_"), TabularLayout.Constraint(0, 1))
    }

  override val component: JPanel = this

  init {
    border = MatteBorder(1, 0, 0, 0, JBColor.border())
    add(firstRow, TabularLayout.Constraint(0, 0))
  }

  override fun getCurrentHeight() = InspectorLayout.UNSUPPORTED_ROW_HEIGHT
  override var expandedSize: Int = InspectorLayout.UNSUPPORTED_ROW_HEIGHT
  override fun setDuration(durationMillis: Int?) {}
}
