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

import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class CellRenderer implements TableCellRenderer {
  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
    TableCellRenderer defaultRenderer = table.getDefaultRenderer(table.getModel().getColumnClass(column));
    Component component = defaultRenderer.getTableCellRendererComponent(table, value, selected, focused, row, column);

    String tooltip;
    if (ConstantColumn.indexMatchesColumn(column, ConstantColumn.KEY)) {
      tooltip = ((StringResourceTableModel) table.getModel()).getKeyProblem(row);
    } else {
      tooltip = ((StringResourceTableModel) table.getModel()).getCellProblem(row, column);
    }
    if (component instanceof JComponent) {
      ((JComponent) component).setToolTipText(tooltip);
    }

    Color foreground = UIUtil.getTableForeground(selected);
    Color background = UIUtil.getTableBackground(selected);
    if (!selected && tooltip != null) {
      if (ConstantColumn.indexMatchesColumn(column, ConstantColumn.KEY)) {
        // If a cell in the Key column is problematic, color the text red
        foreground = JBColor.RED;
      } else {
        // If any other cell is problematic, shade its background a different color
        background = UIUtil.getDecoratedRowColor();
      }
    }

    component.setForeground(foreground);
    component.setBackground(background);

    return component;
  }
}
