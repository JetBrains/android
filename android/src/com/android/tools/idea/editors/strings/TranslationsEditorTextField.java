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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.IntSupplier;

final class TranslationsEditorTextField extends JBTextField {
  TranslationsEditorTextField(@NotNull StringResourceTable table, int column) {
    this(table, () -> column);
  }

  TranslationsEditorTextField(@NotNull StringResourceTable table, @NotNull IntSupplier columnSupplier) {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(@NotNull KeyEvent event) {
        // The text field is only editable when there is a selected cell
        if (table.hasSelectedCell()) {
          JTextComponent textField = (JTextComponent)event.getSource();
          table.getModel().setValueAt(textField.getText(), table.getSelectedModelRowIndex(), columnSupplier.getAsInt());
        }
      }
    });
  }
}
