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
package com.android.tools.idea.editors.strings

import com.android.tools.adtui.stdui.OUTLINE_PROPERTY
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.intellij.ui.DocumentAdapter
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.function.IntSupplier
import javax.swing.event.DocumentEvent

/** A text field that can be wired up to edit a particular column in a [StringResourceTable]. */
internal class TranslationsEditorTextField(
  private val table: StringResourceTable,
  private val columnSupplier: IntSupplier,
  private val validator: (String) -> String? // return an error string if validation field fails
) : MinimumWidthTextField() {
  private var lastSavedValue: String? = null

  override fun setText(text: String?) {
    super.setText(text)
    lastSavedValue = text
  }

  init {
    addActionListener { saveText() }
    addFocusListener(object : FocusAdapter() {
      override fun focusLost(event: FocusEvent) {
        saveText()
      }
    })
    document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        updateErrorState()
      }
    })
  }

  private fun saveText() {
    // Make sure both listeners do not save the same value twice.
    // When creating new entries in a strings file, that could lead to multiple resources being created.
    if (table.hasSelectedCell() && text != lastSavedValue) {
      val error = updateErrorState()
      if (error.isNullOrEmpty()) {
        lastSavedValue = text;
        table.model.setValueAt(text, table.selectedModelRowIndex, columnSupplier.asInt)
      }
    }
  }

  private fun updateErrorState(): String? {
    val error = validator(text)
    putClientProperty(OUTLINE_PROPERTY, if (error.isNullOrEmpty()) null else "error")
    toolTipText = error
    return error
  }
}
