/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.observable.ui

import com.android.tools.idea.observable.core.ObjectProperty
import java.awt.event.ItemEvent.SELECTED
import javax.swing.JRadioButton

/**
 * An observable property that wraps a group of radio buttons and exposes the selected one by mapping it
 * to one of the given objects, for example enum values.
 *
 * @param selected the value corresponding to the initially selected radio button. The value has to be present in the [values] array.
 * @param values the values to map the radio buttons to
 * @param radioButtons the radio buttons
 */
class SelectedRadioButtonProperty<T : Any>(private var selected: T, values: Array<T>, vararg radioButtons: JRadioButton) : ObjectProperty<T>() {
  private val myValueToButtonMap: Map<T, JRadioButton>

  init {
    require(values.size == radioButtons.size) {
      "The number of values (${values.size}) doesn't match the number of radio buttons (${radioButtons.size})"
    }
    myValueToButtonMap = values.zip(radioButtons).toMap()
    myValueToButtonMap.forEach { (value, button) ->
      button.addItemListener { event ->
        if (event.stateChange == SELECTED) {
          selected = value
          notifyInvalidated()
        }
      }
    }
    setDirectly(selected)
  }

  /**
   * Returns the value corresponding to the selected radio button.
   */
  override fun get() = selected

  /**
   * Selects the radio button corresponding to the given value.
   */
  override fun setDirectly(value: T) {
    requireNotNull(myValueToButtonMap[value]) { "Invalid selected value (${value})" }.isSelected = true
  }
}