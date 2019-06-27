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

import java.util.List;
import java.util.stream.Collectors;
import javax.swing.RowSorter;
import javax.swing.event.RowSorterEvent.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SubTableRowSorter<M> extends RowSorter<SubTableModel> {
  @NotNull
  private final RowSorter<M> myDelegate;

  @NotNull
  private final SubTableModel myModel;

  SubTableRowSorter(@NotNull RowSorter<M> delegate, @NotNull SubTableModel model) {
    delegate.addRowSorterListener(event -> {
      Type type = event.getType();

      switch (type) {
        case SORT_ORDER_CHANGED:
          fireSortOrderChanged();
          break;
        case SORTED:
          fireRowSorterChanged(null);
          break;
        default:
          assert false : type;
          break;
      }
    });

    myDelegate = delegate;
    myModel = model;
  }

  @NotNull
  @Override
  public SubTableModel getModel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void toggleSortOrder(int modelColumnIndex) {
    myDelegate.toggleSortOrder(myModel.convertColumnIndexToDelegate(modelColumnIndex));
  }

  @Override
  public int convertRowIndexToModel(int viewRowIndex) {
    return myDelegate.convertRowIndexToModel(viewRowIndex);
  }

  @Override
  public int convertRowIndexToView(int modelRowIndex) {
    return myDelegate.convertRowIndexToView(modelRowIndex);
  }

  @Override
  public void setSortKeys(@Nullable List<? extends SortKey> keys) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<? extends SortKey> getSortKeys() {
    return myDelegate.getSortKeys().stream()
      .filter(key -> myModel.contains(key.getColumn()))
      .map(key -> new SortKey(myModel.convertColumnIndexToModel(key.getColumn()), key.getSortOrder()))
      .collect(Collectors.toList());
  }

  @Override
  public int getViewRowCount() {
    return myDelegate.getViewRowCount();
  }

  @Override
  public int getModelRowCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void modelStructureChanged() {
  }

  @Override
  public void allRowsChanged() {
  }

  @Override
  public void rowsInserted(int startModelRowIndex, int endModelRowIndex) {
  }

  @Override
  public void rowsDeleted(int startModelRowIndex, int endModelRowIndex) {
  }

  @Override
  public void rowsUpdated(int startModelRowIndex, int endModelRowIndex) {
  }

  @Override
  public void rowsUpdated(int startModelRowIndex, int endModelRowIndex, int modelColumnIndex) {
  }
}
