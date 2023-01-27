/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.property.panel.api

import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.property.panel.impl.ui.ExpandableLabel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Header consisting of 2 labels that will be flushed left and right.
 *
 * The layout makes sure there is no overlap between the 2 labels.
 */
class SelectedComponentPanel(private val model: SelectedComponentModel) : JPanel(BorderLayout()) {
  private val left = ExpandableLabel()
  private val right = ExpandableLabel()
  private val listener = ::updateAfterModelChange

  init {
    background = secondaryPanelBackground
    PropertyTextField.addBorderAtTextFieldBorderSize(this)
    left.foreground = JBColor(Gray._192, Gray._128)
    add(left, BorderLayout.WEST)
    add(right, BorderLayout.EAST)
    updateAfterModelChange()
  }

  // The [SelectedComponentPanel] is short-lived. Make sure the listener is removed when the panel goes away.
  override fun addNotify() {
    super.addNotify()
    model.addValueChangedListener(listener)
  }

  override fun removeNotify() {
    super.removeNotify()
    model.removeValueChangedListener(listener)
  }

  private fun updateAfterModelChange() {
    left.icon = model.icon
    left.actualText = model.description
    right.actualText = model.id
  }

  override fun doLayout() {
    super.doLayout()
    if (left.x + left.width > right.x) {
      val insets = border.getBorderInsets(this)
      val halfWidth = Integer.max(0, (width - insets.left - insets.right) / 2)
      val leftBounds = left.bounds
      val rightBounds = right.bounds
      when {
        leftBounds.width < halfWidth -> rightBounds.width = halfWidth * 2 - leftBounds.width
        rightBounds.width < halfWidth -> leftBounds.width = halfWidth * 2 - rightBounds.width
        else -> {
          leftBounds.width = halfWidth
          rightBounds.width = halfWidth
        }
      }
      rightBounds.x = width - insets.right - rightBounds.width
      left.bounds = leftBounds
      right.bounds = rightBounds
    }
  }
}
