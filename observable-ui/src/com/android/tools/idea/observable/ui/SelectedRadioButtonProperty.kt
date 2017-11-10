/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.intellij.util.ArrayUtil
import java.awt.event.ItemEvent
import java.awt.event.ItemEvent.SELECTED
import java.awt.event.ItemListener
import javax.swing.JRadioButton

/**
 * An observable property that wraps a group of radio buttons and exposes the selected one by mapping it
 * to one of the given objects, for example enum values.
 */
class SelectedRadioButtonProperty<T>
/**
 * @param selected the value corresponding to the initially selected radio button
 * @param values the values to map the radio buttons to
 * @param radioButtons the radio buttons
 */
(selected: T, private val values: Array<T>, vararg radioButtons: JRadioButton) : ObjectProperty<T>(), ItemListener {
  private val myRadioButtons: Array<out JRadioButton>

  init {
    val selectedIndex = getSelectedIndex(selected, values)

    if (values.size != radioButtons.size) {
      val msg = "The number of values (${values.size}) doesn't match the number of radio buttons (${radioButtons.size})"
      throw IllegalArgumentException(msg)
    }
    radioButtons[selectedIndex].isSelected = true
    myRadioButtons = radioButtons
    for (radioButton in radioButtons) {
      radioButton.addItemListener(this)
    }
  }

  override fun itemStateChanged(event: ItemEvent) {
    if (event.stateChange == SELECTED) {
      notifyInvalidated()
    }
  }

  /**
   * Returns the value corresponding to the selected radio button, or an empty optional value if no radio button is selected.
   */
  override fun get(): T {
    for (i in myRadioButtons.indices) {
      if (myRadioButtons[i].isSelected) {
        return values[i]
      }
    }
    throw IllegalStateException("No radio button is selected")
  }

  /**
   * Selectes the radio button corresponding to the given value, or unselects all radio buttons if the value is not present.
   */
  override fun setDirectly(value: T) {
    val selectedIndex = getSelectedIndex(value, values)
    myRadioButtons[selectedIndex].isSelected = true
  }

  private fun <T> getSelectedIndex(selected: T, values: Array<T>): Int {
    val selectedIndex = ArrayUtil.find(values, selected)
    if (selectedIndex < 0) {
      throw IllegalArgumentException("Invalid selected value ($selected)")
    }
    return selectedIndex
  }
}
