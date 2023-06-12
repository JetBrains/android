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
package com.android.tools.property.ptable

import com.android.tools.adtui.model.stdui.ValueChangedListener

/**
 * Support for resizing the name column in the a UI with 2 columns like PTable.
 *
 * @param initialValue the initial fraction of the divider position.
 * @param resizeSupported true if the fraction can be changed.
 */
class ColumnFraction(
  initialValue: Float = 0.4f,
  val resizeSupported: Boolean = false
) {
  /**
   * Listeners of changes to the left fraction.
   */
  var listeners = mutableListOf<ValueChangedListener>()

  /**
   * The position of the middle divider as a fraction of the total width.
   */
  var value: Float = initialValue
    set(value) {
      if (!resizeSupported) {
        error("Not supported")
      }
      val changed = field != value
      if (changed) {
      // Don't allow any values outside of 0.01..0.99 (to avoid divide by zero errors).
        field = value.coerceIn(0.01f, 0.99f)
        listeners.toTypedArray().forEach { it.valueChanged() }
      }
    }
}
