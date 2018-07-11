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
import com.android.tools.adtui.model.stdui.EDITOR_NO_COMPLETIONS
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent

const val OUTLINE_PROPERTY = "JComponent.outline"
const val ERROR_VALUE = "error"

/**
 * TextField controlled by an [editorModel].
 */
open class CommonTextField<out M: CommonTextFieldModel>(val editorModel: M) : JBTextField() {

  private var lookup: Lookup<M>? = null
  private var updatingFromModel = false
  private var documentChangeFromSetText = false

  init {
    if (editorModel.editingSupport.completion != EDITOR_NO_COMPLETIONS) {
      @Suppress("LeakingThis")
      val myLookup = Lookup(this)
      registerKeyAction({ enterInLookup() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
      registerKeyAction({ escapeInLookup() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape")
      registerKeyAction({ myLookup.showLookup() }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK), "showCompletions")
      registerKeyAction({ myLookup.selectNext() }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectNext")
      registerKeyAction({ myLookup.selectPrevious() }, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectPrevious")
      registerKeyAction({ myLookup.selectNextPage() }, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "selectNextPage")
      registerKeyAction({ myLookup.selectPreviousPage() }, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "selectPreviousPage")
      registerKeyAction({ myLookup.selectFirst() }, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, KeyEvent.CTRL_MASK), "selectFirst")
      registerKeyAction({ myLookup.selectLast() }, KeyStroke.getKeyStroke(KeyEvent.VK_END, KeyEvent.CTRL_MASK), "selectLast")
      super.addFocusListener(object: FocusAdapter() {
        override fun focusLost(event: FocusEvent) {
          myLookup.close()
        }
      })
      lookup = myLookup
    }
    isFocusable = true
    setFromModel()

    editorModel.addListener(ValueChangedListener { updateFromModel() })
    document.addDocumentListener(object: DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        if (!updatingFromModel) {
          val newText = text
          editorModel.text = newText

          // setText is usually initial setup. Don't show completions here:
          if (!documentChangeFromSetText && (newText.isNotEmpty() || lookup?.isVisible == true)) {
            lookup?.showLookup()
          }
          updateOutline()
        }
      }
    })
  }

  protected open fun updateFromModel() {
    setFromModel()
  }

  fun enterInLookup(): Boolean {
    return lookup?.enter() ?: false
  }

  fun escapeInLookup(): Boolean {
    return lookup?.escape() ?: false
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
    documentChangeFromSetText = true
    try {
      // Avoid flickering: Only update if value is different from current value
      if (!text.equals(super.getText())) {
        super.setText(text)
        UIUtil.resetUndoRedoActions(this)
      }
    }
    finally {
      documentChangeFromSetText = false
    }
  }

  // Update the outline property on component such that the Darcula border will
  // be able to indicate an error by painting a red border.
  private fun updateOutline() {
    // If this text field is an editor in a ComboBox set the property on the ComboBox,
    // otherwise set the property on this text field.
    val component = parent as? JComboBox<*> ?: this as JComponent
    val current = component.getClientProperty(OUTLINE_PROPERTY)
    val (code, _) = editorModel.editingSupport.validation(editorModel.text)
    val newOutline = code.outline
    if (current != newOutline) {
      component.putClientProperty(OUTLINE_PROPERTY, newOutline)
      component.repaint()
    }
  }
}
