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
package com.android.tools.idea.common.property2.impl.model

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.InspectorLineModel

private const val ERROR_NOT_FOCUSABLE = "Component is not focusable"

/**
 * A simple model for a "line" in the property inspector.
 *
 * A custom editor may use this as a base class for its model.
 */
open class GenericInspectorLineModel : InspectorLineModel {
  private var listeners = mutableListOf<ValueChangedListener>()

  override var hidden = false
    set(value) {
      field = value
      fireValueChanged()
    }

  override var visible = true
    get() = field && !hidden
    set(value) {
      field = value
      if (!hidden) {
        fireValueChanged()
      }
    }

  override val focusable: Boolean
    get() = false

  override var focusRequest: Boolean
    get() = error(ERROR_NOT_FOCUSABLE)
    set(value) = error(ERROR_NOT_FOCUSABLE)

  fun addValueChangedListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  fun removeValueChangedListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  protected fun fireValueChanged() {
    listeners.forEach { it.valueChanged() }
  }
}
