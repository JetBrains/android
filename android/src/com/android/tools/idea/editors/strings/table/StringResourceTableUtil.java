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

import com.android.tools.idea.rendering.StringResourceData;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.util.*;

public class StringResourceTableUtil {
  private static final TableCellRenderer CELL_RENDERER = new ColoredTableCellRenderer() {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      String strValue = String.valueOf(value);
      append(strValue);
      // Shades empty cells (missing translations) a different color
      setBackground(strValue.isEmpty() && !selected ? UIUtil.getDecoratedRowColor() : getBackground());
    }
  };

  public static void initTableView(@NotNull final JBTable table) {
    MouseAdapter resizeListener = new ColumnResizeListener(table);
    table.getTableHeader().addMouseListener(resizeListener);
    table.getTableHeader().addMouseMotionListener(resizeListener);

    table.getParent().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        /* If necessary, grows the table to fill the viewport. Does not trigger when the user drags a column to resize
         * (taken care of by ColumnResizeListener).
         */
        if (table.getTableHeader().getResizingColumn() == null) {
          expandToViewportWidthIfNecessary(table, -1);
        }
      }
    });
  }

  public static void initTableData(@NotNull JBTable table, @NotNull StringResourceData data) {
    table.setModel(new StringResourceTableModel(data));

    Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
    while (columns.hasMoreElements()) {
      TableColumn column = columns.nextElement();
      column.setCellRenderer(CELL_RENDERER);
      int index = column.getModelIndex();
      FontMetrics fm = table.getFontMetrics(table.getFont());
      HeaderCellRenderer renderer = index < ConstantColumn.values().length
                                    ? new ConstantHeaderCellRenderer(index, fm)
                                    : new TranslationHeaderCellRenderer(fm, data.getLocales().get(index - ConstantColumn.values().length));
      column.setHeaderRenderer(renderer);
      // Sets Key and Default Value columns to initially display at full width and all others to be collapsed
      setPreferredWidth(column, index < ConstantColumn.values().length ? renderer.getFullExpandedWidth() : renderer.getCollapsedWidth());
    }

    expandToViewportWidthIfNecessary(table, -1);
  }

  // Returns the additional width needed for the table to fill its viewport.  A value <= 0 indicates that the table is sufficiently wide.
  static int getAdditionalWidthToFillViewport(@NotNull JBTable table) {
    return table.getParent().getWidth() - table.getPreferredSize().width;
  }

  /* Expands the table to fill its viewport horizontally by distributing extra width among the columns except the one at ignoreIndex.
   * Caller should pass in -1 for ignoreIndex to specify that this method can touch all of the columns.
   */
  static void expandToViewportWidthIfNecessary(@NotNull JBTable table, int ignoreIndex) {
    if (table.getColumnModel().getColumnCount() < ConstantColumn.values().length) {
      // Table has no data
      return;
    }
    int widthToFillViewport = getAdditionalWidthToFillViewport(table);
    if (widthToFillViewport <= 0) {
      // Table already fills viewport
      return;
    }

    int totalNumColumns = table.getColumnModel().getColumnCount();
    int numColumnsForDistribution = totalNumColumns - ConstantColumn.values().length;
    if (ConstantColumn.values().length <= ignoreIndex && ignoreIndex < totalNumColumns) {
      --numColumnsForDistribution;
    }
    if (numColumnsForDistribution == 0) {
      // No translation columns among which to distribute extra width
      TableColumn column = table.getColumn(ConstantColumn.DEFAULT_VALUE.name);
      setPreferredWidth(column, column.getPreferredWidth() + widthToFillViewport);
      return;
    }
    int extraWidth = widthToFillViewport / numColumnsForDistribution;
    for (int i = ConstantColumn.values().length; i < totalNumColumns; ++i) {
      if (i == ignoreIndex) {
        continue;
      }
      TableColumn column = table.getColumnModel().getColumn(i);
      setPreferredWidth(column, column.getPreferredWidth() + extraWidth);
      widthToFillViewport -= extraWidth;
    }
    // Might still be under width because of integer division
    int resizeIndex = totalNumColumns - 1;
    if (resizeIndex == ignoreIndex) {
      --resizeIndex;
    }
    TableColumn column = table.getColumnModel().getColumn(resizeIndex);
    setPreferredWidth(column, column.getPreferredWidth() + widthToFillViewport);
  }

  /* Sets the width of a column, switching between brief and full column names if necessary.
   * Whenever possible, should be used in place of a direct call to column.setPreferredWidth(width).
   */
  static void setPreferredWidth(@NotNull TableColumn column, int width) {
    column.setPreferredWidth(width);
    toggleColumnNameIfApplicable(column);
  }

  private static void toggleColumnNameIfApplicable(@NotNull TableColumn column) {
    if (column.getHeaderRenderer() instanceof TranslationHeaderCellRenderer) {
      TranslationHeaderCellRenderer renderer = (TranslationHeaderCellRenderer) column.getHeaderRenderer();
      renderer.setUseBriefName(column.getPreferredWidth() < renderer.getMinimumExpandedWidth());
    }
  }
}
