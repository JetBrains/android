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

import com.android.tools.adtui.model.stdui.CommonTextFieldModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.text.PlainDocument

const val OUTLINE_PROPERTY = "JComponent.outline"
const val ERROR_VALUE = "error"

/**
 * TextField controlled by an [editorModel].
 */
open class CommonTextField<out M: CommonTextFieldModel>(val editorModel: M) : JBTextField() {

  private var updatingFromModel = false

  init {
    isFocusable = true
    document = PlainDocument()
    setFromModel()

    editorModel.addListener(ValueChangedListener { updateFromModel() })
    document.addDocumentListener(object: DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        if (!updatingFromModel) {
          editorModel.text = text
          updateOutline()
        }
      }
    })
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  private fun setFromModel() {
    updatingFromModel = true
    try {
      text = editorModel.value
      isEnabled = editorModel.enabled
      isEditable = editorModel.editable
      emptyText.text = editorModel.placeHolderValue
      updateOutline()
    }
    finally {
      updatingFromModel = false
    }
  }

  override fun setText(text: String?) {
    // Avoid flickering: Only update if value is different from current value
    if (!text.equals(super.getText())) {
      super.setText(text)
      UIUtil.resetUndoRedoActions(this)
    }
  }

  // Update the outline property on component such that the Darcula border will
  // be able to indicate an error by painting a red border.
  private fun updateOutline() {
    // If this text field is an editor in a ComboBox set the property on the ComboBox,
    // otherwise set the property on this text field.
    val component = parent as? JComboBox<*> ?: this as JComponent
    val current = component.getClientProperty(OUTLINE_PROPERTY)
    val wanted = if (!editorModel.validate(editorModel.text).isEmpty()) ERROR_VALUE else null
    if (current != wanted) {
      component.putClientProperty(OUTLINE_PROPERTY, wanted)
      component.repaint()
    }
  }
}
