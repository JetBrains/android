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
import javax.swing.JComboBox

open class CommonComboBox<E>(model: CommonComboBoxModel<E>) : JComboBox<E>(model) {

  init {
    setFromModel()

    model.addListener(object: ValueChangedListener {
      override fun valueChanged() {
        updateFromModel()
        repaint()
      }
    })
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    val comboModel = model as? CommonComboBoxModel ?: return
    isEnabled = comboModel.enabled
    if (isEditable != comboModel.editable) {
      super.setEditable(comboModel.editable)
    }
  }

  override fun updateUI() {
    setUI(CommonComboBoxUI())
    revalidate()
  }
}
