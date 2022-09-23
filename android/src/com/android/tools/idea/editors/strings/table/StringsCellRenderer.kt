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

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import java.awt.Font;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StringsCellRenderer extends ColoredTableCellRenderer {
  private static final SimpleTextAttributes CELL_ERROR_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, JBColor.red);

  @Override
  protected void customizeCellRenderer(@NotNull JTable subTable,
                                       @Nullable Object value,
                                       boolean selected,
                                       boolean focusOwner,
                                       int viewRowIndex,
                                       int viewColumnIndex) {
    @SuppressWarnings("unchecked")
    FrozenColumnTable<StringResourceTableModel> frozenColumnTable = ((SubTable<StringResourceTableModel>)subTable).getFrozenColumnTable();

    JTable frozenTable = frozenColumnTable.getFrozenTable();

    if (subTable == frozenTable) {
      customizeCellRenderer(frozenColumnTable, value, viewRowIndex, viewColumnIndex);
      return;
    }

    customizeCellRenderer(frozenColumnTable, value, viewRowIndex, viewColumnIndex + frozenTable.getColumnCount());
  }

  private void customizeCellRenderer(@NotNull FrozenColumnTable<StringResourceTableModel> table,
                                     @Nullable Object value,
                                     int viewRowIndex,
                                     int viewColumnIndex) {
    if (!(value instanceof String)) {
      return;
    }

    String s = (String)value;

    if (StringUtil.containsChar(s, '\n')) {
      s = clip(s);
    }

    int modelRowIndex = table.convertRowIndexToModel(viewRowIndex);
    int modelColumnIndex = table.convertColumnIndexToModel(viewColumnIndex);

    String problem = table.getModel().getCellProblem(modelRowIndex, modelColumnIndex);
    SimpleTextAttributes attributes;

    if (problem == null) {
      attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    else if (modelColumnIndex == StringResourceTableModel.KEY_COLUMN) {
      attributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else {
      attributes = CELL_ERROR_ATTRIBUTES;
    }

    Font currentFont = table.getFont();
    Font f = StringResourceEditor.getFont(currentFont);

    if (!currentFont.equals(f)) {
      setFont(f);
    }

    setToolTipText(problem);
    append(s, attributes);
  }

  private static String clip(String str) {
    int end = str.indexOf('\n');
    return end < 0 ? str : str.substring(0, end) + "[...]";
  }
}
