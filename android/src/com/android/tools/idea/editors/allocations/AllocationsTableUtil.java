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

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.AllocationInfo;
import com.android.tools.idea.editors.allocations.AllocationsRowListener;
import com.android.tools.idea.editors.allocations.AllocationsRowSorter;
import com.android.tools.idea.editors.allocations.AllocationsTableModel;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.BaseTableView;
import com.intellij.ui.table.JBTable;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import java.awt.*;

public class AllocationsTableUtil {
  private static final Storage.PropertiesComponentStorage STORAGE =
          new Storage.PropertiesComponentStorage("android.allocationsview.columns");

  @VisibleForTesting
  public enum Column {
    ALLOCATION_ORDER("Allocation Order", 0),
    ALLOCATED_CLASS("Allocated Class", "com.sample.data.AllocatedClass"),
    ALLOCATION_SIZE("Allocation Size", 0),
    THREAD_ID("Thread Id", 0),
    ALLOCATION_SITE("Allocation Site", "com.sample.data.AllocationSite.method(AllocationSite.java:000)");

    public final String description;
    public final Object sampleData;
    Column(String description, Object sampleData) {
      this.description = description;
      this.sampleData = sampleData;
    }
  }

  public static void setUpTable(@NotNull final JBTable allocationsTable, @Nullable ConsoleView consoleView) {
    allocationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    new TableSpeedSearch(allocationsTable) {
      @Override
      public int getElementCount() {
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };
    allocationsTable.getSelectionModel().addListSelectionListener(new AllocationsRowListener(allocationsTable, consoleView));
    allocationsTable.setModel(new AllocationsTableModel(new AllocationInfo[]{}));
    allocationsTable.setRowSorter(new AllocationsRowSorter(allocationsTable.getModel()));

    if (STORAGE.get("widthsSet") == null) {
      STORAGE.put("widthsSet", Boolean.toString(true));
      setDefaultColumnWidths(allocationsTable);
      BaseTableView.storeWidth(STORAGE, allocationsTable.getColumnModel());
    }
    BaseTableView.restore(STORAGE, allocationsTable);

    allocationsTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      @Override
      public void columnAdded(TableColumnModelEvent e) {
      }

      @Override
      public void columnRemoved(TableColumnModelEvent e) {
      }

      @Override
      public void columnMoved(TableColumnModelEvent e) {
        BaseTableView.store(STORAGE, allocationsTable);
      }

      @Override
      public void columnMarginChanged(ChangeEvent e) {
        BaseTableView.storeWidth(STORAGE, allocationsTable.getColumnModel());
      }

      @Override
      public void columnSelectionChanged(ListSelectionEvent e) {
      }
    });
  }

  private static void setDefaultColumnWidths(@NotNull JBTable allocationsTable) {
    Column[] columns = Column.values();
    int cumulativeWidth = 0;
    int[] defaultWidths = new int[columns.length];
    FontMetrics metrics = allocationsTable.getFontMetrics(allocationsTable.getFont());
    for (Column column : columns) {
      // Multiples width by ~1.5 so text is not right against column sides
      int columnWidth = 3 * Math.max(metrics.stringWidth(column.description), metrics.stringWidth(String.valueOf(column.sampleData))) / 2;
      defaultWidths[column.ordinal()] = columnWidth;
      if (column != Column.ALLOCATION_SITE) {
        cumulativeWidth += columnWidth;
      }
    }
    // If possible, uses remaining width, which makes the table respect the preferred column widths exactly.
    int remainingWidth = allocationsTable.getWidth() - cumulativeWidth;
    if (remainingWidth > 0) {
      defaultWidths[Column.ALLOCATION_SITE.ordinal()] = remainingWidth;
    }
    for (Column column : columns) {
      allocationsTable.getColumn(column.description).setPreferredWidth(defaultWidths[column.ordinal()]);
    }
  }
}
