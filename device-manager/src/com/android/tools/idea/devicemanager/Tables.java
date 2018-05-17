/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.android.tools.adtui.common.ColoredIconGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Component;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Tables {
  private Tables() {
  }

  public static @NotNull Color getBackground(@NotNull JTable table, boolean selected) {
    if (selected) {
      return table.getSelectionBackground();
    }

    return table.getBackground();
  }

  public static @Nullable Border getBorder(boolean selected, boolean focused) {
    return getBorder(selected, focused, UIManager::getBorder);
  }

  @VisibleForTesting
  static @Nullable Border getBorder(boolean selected, boolean focused, @NotNull Function<Object, Border> getBorder) {
    if (!focused) {
      return getBorder.apply("Table.cellNoFocusBorder");
    }

    if (selected) {
      return getBorder.apply("Table.focusSelectedCellHighlightBorder");
    }

    return getBorder.apply("Table.focusCellHighlightBorder");
  }

  public static @NotNull Color getForeground(@NotNull JTable table, boolean selected) {
    if (selected) {
      return table.getSelectionForeground();
    }

    return table.getForeground();
  }

  static @NotNull Icon getIcon(@NotNull JTable table, boolean selected, @NotNull Icon icon) {
    if (selected) {
      return ColoredIconGenerator.INSTANCE.generateColoredIcon(icon, table.getSelectionForeground());
    }

    return icon;
  }

  public static void setWidths(@NotNull TableColumn column, int width) {
    column.setMinWidth(width);
    column.setMaxWidth(width);
    column.setPreferredWidth(width);
  }

  public static void setWidths(@NotNull TableColumn column, int width, int minWidth) {
    column.setMinWidth(minWidth);
    column.setMaxWidth(width);
    column.setPreferredWidth(width);
  }

  public static int getPreferredColumnWidth(@NotNull JTable table, int viewColumnIndex, int minPreferredWidth) {
    OptionalInt width = IntStream.range(-1, table.getRowCount())
      .map(viewRowIndex -> getPreferredCellWidth(table, viewRowIndex, viewColumnIndex))
      .max();

    if (!width.isPresent()) {
      return minPreferredWidth;
    }

    return Math.max(width.getAsInt(), minPreferredWidth);
  }

  private static int getPreferredCellWidth(@NotNull JTable table, int viewRowIndex, int viewColumnIndex) {
    Component component;

    if (viewRowIndex == -1) {
      TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
      Object value = table.getColumnModel().getColumn(viewColumnIndex).getHeaderValue();

      component = renderer.getTableCellRendererComponent(table, value, false, false, -1, viewColumnIndex);
    }
    else {
      component = table.prepareRenderer(table.getCellRenderer(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex);
    }

    return component.getPreferredSize().width + JBUI.scale(8);
  }
}
