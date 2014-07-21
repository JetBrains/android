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
package com.android.tools.idea.editors.allocations;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class AllocationsFilterUtil {
  public static void setUpFiltering(
    @NotNull final JBTable allocationsTable, @NotNull final JBTextField filterField, @NotNull final JBCheckBox includeTraceCheckBox) {
    includeTraceCheckBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateFilter(allocationsTable, filterField, includeTraceCheckBox);
      }
    });
    filterField.getDocument().addDocumentListener(new AllocationsFilterListener(allocationsTable, filterField, includeTraceCheckBox));
  }

  private static class AllocationsFilterListener implements DocumentListener {
    private JBTable myTable;
    private JBTextField myField;
    private JBCheckBox myCheckBox;

    public AllocationsFilterListener(@NotNull JBTable table, @NotNull JBTextField field, @NotNull JBCheckBox checkBox) {
      myTable = table;
      myField = field;
      myCheckBox = checkBox;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      updateFilter(myTable, myField, myCheckBox);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
      updateFilter(myTable, myField, myCheckBox);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      updateFilter(myTable, myField, myCheckBox);
    }
  }

  private static void updateFilter(@NotNull JBTable table, @NotNull JBTextField field, @NotNull JBCheckBox checkBox) {
    RowSorter<? extends TableModel> rowSorter = table.getRowSorter();
    if (rowSorter instanceof AllocationsRowSorter) {
      ((AllocationsRowSorter) rowSorter).setRowFilter(new AllocationsRowFilter((AllocationsTableModel) table.getModel(), field, checkBox));
    }
  }
}
