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
package com.android.tools.property.panel.impl.model.util

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.api.TableSupport

class FakePropertyEditorModel(override var property: PropertyItem) : PropertyEditorModel {
  override var value: String = property.value ?: ""

  override var defaultValue: String = property.defaultValue ?: ""

  override var visible = true

  override var readOnly = false

  var focusWasRequested = false
    private set

  var toggleCount = 0
    private set

  override fun cancelEditing(): Boolean {
    return true
  }

  override fun requestFocus() {
    focusWasRequested = true
  }

  override fun toggleValue() {
    toggleCount++
  }

  override val hasFocus = false

  override var isUsedInRendererWithSelection = false

  override var isExpandedTableItem = false

  override var tableExpansionState = TableExpansionState.NORMAL

  override var isCustomHeight = false

  override var tableSupport: TableSupport? = null

  override var lineModel: InspectorLineModel? = null

  override fun refresh() {
    value = property.value ?: ""
  }

  override fun addListener(listener: ValueChangedListener) {
    throw NotImplementedError()
  }

  override fun removeListener(listener: ValueChangedListener) {
    throw NotImplementedError()
  }
}
