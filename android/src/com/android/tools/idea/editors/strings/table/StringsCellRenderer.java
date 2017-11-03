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

import com.android.tools.idea.editors.strings.FontUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

public class StringsCellRenderer extends ColoredTableCellRenderer {
  private static final SimpleTextAttributes CELL_ERROR_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, JBColor.red);

  @Override
  protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
    if (!(value instanceof String)) {
      return;
    }

    String s = (String)value;
    if (shouldClip(s)) {
      s = clip(s);
    }

    row = table.convertRowIndexToModel(row);
    column = table.convertColumnIndexToModel(column);

    String problem = ((StringResourceTableModel)table.getModel()).getCellProblem(row, column);
    SimpleTextAttributes attributes;

    if (problem == null) {
      attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    else if (column == StringResourceTableModel.KEY_COLUMN) {
      attributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else {
      attributes = CELL_ERROR_ATTRIBUTES;
    }

    Font currentFont = table.getFont();
    Font f = FontUtil.getFontAbleToDisplay(s, currentFont);

    if (!currentFont.equals(f)) {
      setFont(f);
    }

    setToolTipText(problem);
    append(s, attributes);
  }

  public static boolean shouldClip(String s) {
    return StringUtil.containsChar(s, '\n');
  }

  private static String clip(String str) {
    int end = str.indexOf('\n');
    return end < 0 ? str : str.substring(0, end) + "[...]";
  }
}
