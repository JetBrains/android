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

import com.android.ddmlib.AllocationInfo;
import com.android.tools.idea.editors.allocations.AllocationsTableUtil.Column;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

public class AllocationsTableModel extends AbstractTableModel {
  AllocationInfo[] myAllocations;

  public AllocationsTableModel(@NotNull AllocationInfo[] allocations) {
    setAllocations(allocations);
  }

  public void setAllocations(@NotNull AllocationInfo[] allocations) {
    myAllocations = allocations;
  }

  @NotNull
  public AllocationInfo getAllocation(int modelRow) {
    return myAllocations[modelRow];
  }

  @Override
  public int getRowCount() {
    return myAllocations.length;
  }

  @Override
  public int getColumnCount() {
    return Column.values().length;
  }

  @Override
  @Nullable
  public Object getValueAt(int row, int column) {
    switch (Column.values()[column]) {
      case ALLOCATION_ORDER:
        return myAllocations[row].getAllocNumber();
      case ALLOCATED_CLASS:
        return myAllocations[row].getAllocatedClass();
      case ALLOCATION_SIZE:
        return myAllocations[row].getSize();
      case THREAD_ID:
        return myAllocations[row].getThreadId();
      case ALLOCATION_SITE:
        return myAllocations[row].getAllocationSite();
      default:
        return null;
    }
  }

  @Override
  @NotNull
  public String getColumnName(int column) {
    return Column.values()[column].description;
  }

  @Override
  @NotNull
  public Class getColumnClass(int c) {
    return Column.values()[c].sampleData.getClass();
  }
}