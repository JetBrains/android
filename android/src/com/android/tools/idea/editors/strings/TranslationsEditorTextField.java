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

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.IntSupplier;

final class TranslationsEditorTextField extends JBTextField {
  private final StringResourceTable myTable;

  /**
   * When a translation is selected in the table the default value text field is editable. When a developer enters a value in the default
   * value text field the supplier always returns the default value table column and not the selected one. When the developer enters a value
   * in the translation text field the supplier returns the selected table column. That is why this is a supplier.
   */
  private final IntSupplier myColumnSupplier;

  private SetTableValueAtTimer myTimer;

  TranslationsEditorTextField(@NotNull StringResourceTable table, int column) {
    this(table, () -> column);
  }

  TranslationsEditorTextField(@NotNull StringResourceTable table, @NotNull IntSupplier columnSupplier) {
    myTable = table;
    myColumnSupplier = columnSupplier;

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(@NotNull KeyEvent event) {
        int rowCount = table.getSelectedRowCount();
        int columnCount = table.getSelectedColumnCount();

        if (rowCount != 1 || columnCount != 1) {
          // The text field is not editable when there's more than one selected row or column
          return;
        }

        if (myTimer == null || myTimer.isDone()) {
          myTimer = new SetTableValueAtTimer(myTable, myColumnSupplier);
        }

        myTimer.setValue(((JTextComponent)event.getSource()).getText());
        myTimer.restart();
      }
    });
  }

  private static final class SetTableValueAtTimer extends Timer {
    private String myValue;
    private boolean myDone;

    private SetTableValueAtTimer(@NotNull StringResourceTable table, @NotNull IntSupplier columnSupplier) {
      super(500, null);
      setRepeats(false);

      int row = table.getSelectedRowModelIndex();
      int column = columnSupplier.getAsInt();

      addActionListener(event -> {
        table.getModel().setValueAt(myValue, row, column);
        table.refilter();

        myDone = true;
      });
    }

    private void setValue(@NotNull String value) {
      myValue = value;
    }

    private boolean isDone() {
      return myDone;
    }
  }
}