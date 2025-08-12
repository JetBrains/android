/*
 * Copyright (C) 2019 The Android Open Source Project
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

import javax.swing.DefaultRowSorter;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FrozenColumnTableRowSorter<M> {
  @NotNull
  private final DefaultRowSorter<M, Integer> myDelegate;

  @NotNull
  private final RowSorter<SubTableModel> myFrozenTableRowSorter;

  @NotNull
  private final RowSorter<SubTableModel> myScrollableTableRowSorter;

  public FrozenColumnTableRowSorter(@NotNull DefaultRowSorter<M, Integer> delegate, @NotNull FrozenColumnTable table) {
    delegate.setMaxSortKeys(1);

    myDelegate = delegate;
    myFrozenTableRowSorter = new SubTableRowSorter<>(delegate, (SubTableModel)table.getFrozenTable().getModel());
    myScrollableTableRowSorter = new SubTableRowSorter<>(delegate, (SubTableModel)table.getScrollableTable().getModel());
  }

  @NotNull
  public RowSorter<SubTableModel> getFrozenTableRowSorter() {
    return myFrozenTableRowSorter;
  }

  @NotNull
  public RowSorter<SubTableModel> getScrollableTableRowSorter() {
    return myScrollableTableRowSorter;
  }

  @Nullable
  public Object getRowFilter() {
    return myDelegate.getRowFilter();
  }

  void setRowFilter(@Nullable RowFilter<M, Integer> rowFilter) {
    myDelegate.setRowFilter(rowFilter);
  }
}
