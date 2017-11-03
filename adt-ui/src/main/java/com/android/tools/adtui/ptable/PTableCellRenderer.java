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

package com.android.tools.adtui.ptable;

import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public abstract class PTableCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {

  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table, @NotNull Object value,
                                                 boolean isSelected, boolean hasFocus, int row, int col) {
    if (!(table instanceof PTable && value instanceof PTableItem)) {
      return this;
    }

    // PTable shows focus for the entire row. Not per cell.
    boolean rowIsLead = table.getSelectionModel().getLeadSelectionIndex() == row;
    hasFocus = table.hasFocus() && rowIsLead;

    clear();
    setBorder(null);
    setPaintFocusBorder(hasFocus);
    setFont(table.getFont());
    if (isSelected) {
      setForeground(UIUtil.getTreeForeground(true, hasFocus));
      setBackground(UIUtil.getTreeSelectionBackground(hasFocus));
    }
    else {
      setForeground(table.getForeground());
      setBackground(table.getBackground());
    }
    customizeCellRenderer((PTable)table, (PTableItem)value, isSelected, hasFocus, row, col);
    return this;
  }

  protected abstract void customizeCellRenderer(
    @NotNull PTable table, @NotNull PTableItem item, boolean selected, boolean hasFocus, int row, int column);
}
