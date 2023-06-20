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

import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.intellij.ui.components.JBTextField
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.function.IntSupplier
import javax.swing.text.JTextComponent

/** A text field that can be wired up to edit a particular column in a [StringResourceTable]. */
internal class TranslationsEditorTextField(table: StringResourceTable, columnSupplier: IntSupplier) : JBTextField() {
  init {
    val adapter = object : KeyAdapter() {
      override fun keyReleased(event: KeyEvent) {
        // The text field is only editable when there is a selected cell
        if (table.hasSelectedCell()) table.model.setValueAt(text, table.selectedModelRowIndex, columnSupplier.asInt)
      }
    }
    addKeyListener(adapter)
  }
}