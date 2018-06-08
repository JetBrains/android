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
package com.android.tools.idea.common.property2.impl.ui

import com.android.SdkConstants
import com.android.annotations.VisibleForTesting
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.impl.model.BooleanPropertyEditorModel
import com.android.tools.idea.common.property2.impl.support.EditorFocusListener
import icons.StudioIcons
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.KeyStroke

/**
 * A standard control for editing a boolean property.
 */
class PropertyCheckBox(private val propertyModel: BooleanPropertyEditorModel) : JCheckBox() {
  private var stateChangeFromModel = false

  @VisibleForTesting
  var state: Boolean
    get() = model.isSelected
    set(value) { model.isSelected = value }

  init {
    icon = StudioIcons.LayoutEditor.Properties.TEXT_ALIGN_CENTER
    state = toStateValue(propertyModel.value)
    registerKeyAction({ propertyModel.enterKeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
    registerKeyAction({ propertyModel.f1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "help")
    registerKeyAction({ propertyModel.shiftF1KeyPressed() }, KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK), "help2")

    propertyModel.addListener(ValueChangedListener { handleValueChanged() })
    addFocusListener(EditorFocusListener(propertyModel, { fromStateValue(state) }))
    model.addChangeListener {
      if (!stateChangeFromModel) {
        propertyModel.value = fromStateValue(model.isSelected)
      }
    }
  }

  private fun handleValueChanged() {
    stateChangeFromModel = true
    try {
      state = toStateValue(propertyModel.value)
    }
    finally {
      stateChangeFromModel = false
    }
    isVisible = propertyModel.visible
    if (propertyModel.focusRequest && !isFocusOwner) {
      requestFocusInWindow()
    }
  }

  override fun getToolTipText(): String? {
    return propertyModel.tooltip
  }

  private fun toStateValue(value: String?) = value?.compareTo(SdkConstants.VALUE_TRUE, ignoreCase = true) == 0

  private fun fromStateValue(selected: Boolean) = if (selected) SdkConstants.VALUE_TRUE else SdkConstants.VALUE_FALSE
}
