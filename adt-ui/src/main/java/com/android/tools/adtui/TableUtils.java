/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.adtui;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public final class TableUtils {
  private TableUtils() {
  }

  public static void paste(@NotNull JTable table, @NotNull Transferable transferable) {
    if (table.getSelectedRowCount() != 1 || table.getSelectedColumnCount() != 1) {
      return;
    }

    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
      return;
    }

    int row = table.getSelectedRow();
    int rowCount = table.getRowCount();

    int selectedColumn = table.getSelectedColumn();
    int columnCount = table.getColumnCount();

    for (String values : getTransferDataAsString(transferable).split("\n")) {
      if (row >= rowCount) {
        break;
      }

      int column = selectedColumn;

      for (Object value : values.split("\t")) {
        if (column >= columnCount) {
          break;
        }

        table.setValueAt(value, row, column++);
      }

      row++;
    }
  }

  @NotNull
  private static String getTransferDataAsString(@NotNull Transferable transferable) {
    try {
      return (String)transferable.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException exception) {
      Logger.getInstance(TableUtils.class).warn(exception);
      return "";
    }
  }

  public static void selectCellAt(@NotNull JTable table, int row, int column) {
    table.setRowSelectionInterval(row, row);
    table.setColumnSelectionInterval(column, column);
  }
}
