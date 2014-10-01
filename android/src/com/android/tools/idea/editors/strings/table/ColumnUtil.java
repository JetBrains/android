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

import com.android.tools.idea.rendering.Locale;
import com.intellij.ui.BooleanTableCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;

public class ColumnUtil {
  private static final TableCellRenderer CELL_RENDERER = new StringsCellRenderer();

  public static void setColumns(@NotNull JTable table) {
    StringResourceTableModel model = (StringResourceTableModel) table.getModel();

    Enumeration<TableColumn> columns = table.getColumnModel().getColumns();
    while (columns.hasMoreElements()) {
      TableColumn column = columns.nextElement();
      if (column.getModelIndex() == ConstantColumn.UNTRANSLATABLE.ordinal()) {
        column.setCellRenderer(new BooleanTableCellRenderer());
      }
      else {
        column.setCellRenderer(CELL_RENDERER);
      }
      int index = column.getModelIndex();
      FontMetrics fontMetrics = table.getFontMetrics(table.getFont());
      Locale locale = model.localeOfColumn(index);
      HeaderCellRenderer renderer =
        locale == null ? new ConstantHeaderCellRenderer(index, fontMetrics) : new TranslationHeaderCellRenderer(fontMetrics, locale);
      column.setHeaderRenderer(renderer);
      // Sets Key and Default Value columns to initially display at full width and all others to be collapsed
      int width;
      if (ConstantColumn.KEY.ordinal() == index ||
          ConstantColumn.DEFAULT_VALUE.ordinal() == index) {
        width = renderer.getFullExpandedWidth();
      } else {
        width = renderer.getCollapsedWidth();
      }
      setPreferredWidth(column, width);
    }

    expandToViewportWidthIfNecessary(table, -1);
  }

  /**
   * Returns the additional width needed for the table to fill its viewport.  A value <= 0 indicates that the table is sufficiently wide.
   */
  static int getAdditionalWidthToFillViewport(@NotNull JTable table) {
    return table.getParent().getWidth() - table.getPreferredSize().width;
  }

  /**
   * Expands the table to fill its viewport horizontally by distributing extra width among the columns except the one at ignoreIndex.
   * Caller should pass in -1 for ignoreIndex to specify that this method can touch all of the columns.
   */
  static void expandToViewportWidthIfNecessary(@NotNull JTable table, int ignoreIndex) {
    if (table.getColumnModel().getColumnCount() < ConstantColumn.COUNT) {
      // Table has no data
      return;
    }
    int widthToFillViewport = getAdditionalWidthToFillViewport(table);
    if (widthToFillViewport <= 0) {
      // Table already fills viewport
      return;
    }

    int totalNumColumns = table.getColumnModel().getColumnCount();
    int numColumnsForDistribution = totalNumColumns - ConstantColumn.COUNT;
    if (ConstantColumn.COUNT <= ignoreIndex && ignoreIndex < totalNumColumns) {
      --numColumnsForDistribution;
    }
    if (numColumnsForDistribution == 0) {
      // No translation columns among which to distribute extra width
      TableColumn column = table.getColumn(ConstantColumn.DEFAULT_VALUE.name);
      setPreferredWidth(column, column.getPreferredWidth() + widthToFillViewport);
      return;
    }
    int extraWidth = widthToFillViewport / numColumnsForDistribution;
    for (int i = ConstantColumn.COUNT; i < totalNumColumns; ++i) {
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

  /**
   * Sets the width of a column, switching between brief and full column names if necessary.
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
