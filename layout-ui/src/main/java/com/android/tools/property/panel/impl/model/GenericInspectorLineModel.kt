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
package com.android.tools.property.panel.impl.model

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.InspectorLineModel
import kotlin.properties.Delegates

private const val ERROR_NOT_FOCUSABLE = "Component is not focusable"

/**
 * A simple model for a "line" in the property inspector.
 *
 * A custom editor may use this as a base class for its model.
 */
open class GenericInspectorLineModel : InspectorLineModel {
  protected var listeners = mutableListOf<ValueChangedListener>()

  override var parent: InspectorLineModel? = null

  override var hidden by Delegates.observable(false) { _, old, new -> if (old != new) fireValueChanged() }

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

  override var enabled by Delegates.observable(true) { _, _, _ -> fireValueChanged() }

  override fun requestFocus() {
    error(ERROR_NOT_FOCUSABLE)
  }

  override fun refresh() {
    fireValueChanged()
  }

  override fun addValueChangedListener(listener: ValueChangedListener) {
    listeners.add(listener)
  }

  override fun removeValueChangedListener(listener: ValueChangedListener) {
    listeners.remove(listener)
  }

  protected fun fireValueChanged() {
    listeners.toTypedArray().forEach { it.valueChanged() }
  }
}

/**
 * This class exists for the benefit of unit testing only.
 *
 * The model is used for generated separators around title elements in the properties panel.
 */
class SeparatorLineModel: GenericInspectorLineModel()
