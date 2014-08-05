/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;

public class CellSelectionListener implements ListSelectionListener {
  private final JTable myTable;
  private final JTextComponent myKeyPane;
  private final JTextComponent myDefaultValuePane;
  private final JTextComponent myTranslationPane;

  public CellSelectionListener(@NotNull JTable table, @NotNull JTextComponent keyPane, @NotNull JTextComponent defaultValuePane,
                               @NotNull JTextComponent translationPane) {
    myTable = table;
    myKeyPane = keyPane;
    myDefaultValuePane = defaultValuePane;
    myTranslationPane = translationPane;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (e.getValueIsAdjusting()) {
      return;
    }
    if (myTable.getSelectedRowCount() != 1 || myTable.getSelectedColumnCount() != 1) {
      return;
    }
    showCellDetail(myTable.getSelectedRow(), myTable.getSelectedColumn());
  }

  private void showCellDetail(int row, int column) {
    if (row < 0 || column < 0) {
      return;
    }
    StringResourceTableModel model = (StringResourceTableModel) myTable.getModel();
    setText(myKeyPane, String.valueOf(model.getValue(row, ConstantColumn.KEY.ordinal())));
    setText(myDefaultValuePane, String.valueOf(model.getValue(row, ConstantColumn.DEFAULT_VALUE.ordinal())));
    String rawText = "";
    if (column >= ConstantColumn.COUNT) {
      rawText = String.valueOf(model.getValue(row, column));
    }
    setText(myTranslationPane, rawText);
  }

  private static void setText(@NotNull JTextComponent component, @NotNull String text) {
    component.setText(text);
    component.setCaretPosition(0);
  }
}
