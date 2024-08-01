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
package com.android.tools.property.panel.impl.ui

import com.android.SdkConstants
import com.android.tools.property.panel.api.EditorContext
import com.android.tools.property.panel.api.HelpSupport
import com.android.tools.property.panel.impl.model.BooleanPropertyEditorModel
import com.android.tools.property.panel.impl.support.EditorFocusListener
import com.android.tools.property.panel.impl.support.HelpSupportBinding
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox

/** A standard control for editing a boolean property. */
class PropertyCheckBox(model: BooleanPropertyEditorModel, context: EditorContext) :
  PropertyTextFieldWithLeftButton(model, context, CustomCheckBox(model)) {

  private val checkBox = leftComponent as CustomCheckBox

  @VisibleForTesting
  var state: Boolean
    get() = checkBox.state
    set(value) {
      checkBox.state = value
    }

  override fun updateFromModel() {
    super.updateFromModel()
    checkBox.updateFromModel()
  }
}

private class CustomCheckBox(private val propertyModel: BooleanPropertyEditorModel) :
  JCheckBox(), UiDataProvider {
  private var stateChangeFromModel = false

  @VisibleForTesting
  var state: Boolean
    get() = model.isSelected
    set(value) {
      model.isSelected = value
    }

  init {
    isOpaque = false // This avoids obscuring the text field border
    state = toStateValue(propertyModel.value)
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    HelpSupportBinding.registerHelpKeyActions(this, { propertyModel.property })

    addFocusListener(EditorFocusListener(this, propertyModel))
    model.addChangeListener {
      if (!stateChangeFromModel) {
        propertyModel.value = fromStateValue(model.isSelected)
      }
    }
    PropertyTextField.addBorderAtTextFieldBorderSize(this)
  }

  fun updateFromModel() {
    stateChangeFromModel = true
    try {
      state = toStateValue(propertyModel.value)
      isFocusable = !propertyModel.readOnly
    } finally {
      stateChangeFromModel = false
    }
  }

  override fun getToolTipText(): String? {
    return propertyModel.tooltip
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[HelpSupport.PROPERTY_ITEM] = propertyModel.property
  }

  private fun toStateValue(value: String?) =
    value?.compareTo(SdkConstants.VALUE_TRUE, ignoreCase = true) == 0

  private fun fromStateValue(selected: Boolean) =
    if (selected) SdkConstants.VALUE_TRUE else SdkConstants.VALUE_FALSE
}
