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
package com.android.tools.adtui.stdui

import com.android.tools.adtui.stdui.StandardDimensions.HORIZONTAL_PADDING
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.plaf.basic.BasicComboBoxRenderer

/**
 * Default renderer for [CommonComboBox]
 */
open class CommonComboBoxRenderer : BasicComboBoxRenderer() {

  override fun getListCellRendererComponent(
    list: JList<*>,
    value: Any?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ) : Component {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JComponent
    component.componentOrientation = list.componentOrientation
    if (list.componentOrientation.isLeftToRight) {
      component.border = JBUI.Borders.emptyLeft(padding(index))
    }
    else {
      component.border = JBUI.Borders.emptyRight(padding(index))
    }
    return component
  }

  companion object {
    // The left padding should be 0 when displaying the current value (index = -1).
    // Otherwise use the standard padding of [HORIZONTAL_PADDING].
    // This method can be used by a custom cell renderer.
    fun padding(index: Int): Int {
      return if (index < 0) 0 else HORIZONTAL_PADDING
    }
  }

  open class UIResource : CommonComboBoxRenderer(), javax.swing.plaf.UIResource
}
