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

import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.util.ui.JBUI
import java.awt.Insets
import javax.swing.JComboBox

open class CommonComboBox<E>(model: CommonComboBoxModel<E>) : JComboBox<E>(model) {

  init {
    updateFromModel(model)
    background = StandardColors.BACKGROUND_COLOR
    isFocusable = true

    model.addListener(object: ValueChangedListener {
      override fun valueChanged() {
        updateFromModel(model)
        repaint()
      }
    })
  }

  private fun updateFromModel(model: CommonComboBoxModel<E>) {
    setEditable(model.editable)
    isEnabled = model.enabled
  }

  override fun updateUI() {
    setUI(CommonComboBoxUI())
    revalidate()
  }

  override fun getInsets(): Insets {
    val insets = JBUI.insets(
        StandardDimensions.VERTICAL_PADDING,
        StandardDimensions.HORIZONTAL_PADDING,
        StandardDimensions.VERTICAL_PADDING,
        StandardDimensions.HORIZONTAL_PADDING
    )
    val fromBorder = border?.getBorderInsets(this) ?: JBUI.insets(0)
    insets.left += fromBorder.left
    insets.right += fromBorder.right
    insets.top += fromBorder.top
    insets.bottom += fromBorder.bottom
    return insets
  }
}
