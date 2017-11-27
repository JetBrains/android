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
package com.android.tools.adtui.model.stdui

import javax.swing.DefaultComboBoxModel

class DefaultCommonComboBoxModel<Item>() : DefaultComboBoxModel<Item>(), CommonComboBoxModel<Item> {
  private val listeners = mutableListOf<ValueChangedListener>()

  constructor(elements: List<Item>) : this() {
    elements.forEach { addElement(it) }
  }

  override var placeHolderValue = ""
    set(value) {
      field = value
      fireValueChanged()
    }

  override var enabled = true
    set(value) {
      field = value
      fireValueChanged()
    }

  override var editable = true
    set(value) {
      field = value
      fireValueChanged()
    }

  override fun validationError(editedValue: String): String {
    return if (editedValue == "Error") "Error is not a valid value" else ""
  }

  override fun addListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: ValueChangedListener) {
    listeners.remove(listener)
  }

  private fun fireValueChanged() {
    listeners.forEach { it.valueChanged() }
  }
}
