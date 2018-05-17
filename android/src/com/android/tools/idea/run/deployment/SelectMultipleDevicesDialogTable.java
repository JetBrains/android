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
package com.android.tools.idea.run.deployment;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

final class SelectMultipleDevicesDialogTable extends JBTable {
  SelectMultipleDevicesDialogTable() {
    JTableHeader header = getTableHeader();

    header.setReorderingAllowed(false);
    header.setResizingAllowed(false);

    setDefaultEditor(Boolean.class, new BooleanTableCellEditor());
    setRowHeight(JBUI.scale(40));
    setRowSelectionAllowed(false);
  }

  @NotNull Set<Target> getSelectedTargets() {
    return ((SelectMultipleDevicesDialogTableModel)dataModel).getSelectedTargets();
  }

  void setSelectedTargets(@NotNull Set<Target> selectedTargets) {
    ((SelectMultipleDevicesDialogTableModel)dataModel).setSelectedTargets(selectedTargets);
  }

  boolean isSelected(int viewRowIndex) {
    int modelRowIndex = convertRowIndexToModel(viewRowIndex);
    return (boolean)dataModel.getValueAt(modelRowIndex, SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX);
  }

  @VisibleForTesting
  void setSelected(@SuppressWarnings("SameParameterValue") boolean selected, @SuppressWarnings("SameParameterValue") int viewRowIndex) {
    dataModel.setValueAt(selected, convertRowIndexToModel(viewRowIndex), SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX);
  }

  @NotNull
  @VisibleForTesting
  List<List<Object>> getData() {
    List<List<Object>> data = new ArrayList<>(1 + getRowCount());

    List<Object> columnNames = IntStream.range(0, getColumnCount())
      .mapToObj(this::getColumnName)
      .collect(Collectors.toList());

    data.add(columnNames);

    IntStream.range(0, getRowCount())
      .mapToObj(this::getRowAt)
      .forEach(data::add);

    return data;
  }

  @NotNull
  private List<Object> getRowAt(int viewRowIndex) {
    return IntStream.range(0, getColumnCount())
      .mapToObj(viewColumnIndex -> getValueAt(viewRowIndex, viewColumnIndex))
      .collect(Collectors.toList());
  }

  @Override
  public void setModel(@NotNull TableModel model) {
    super.setModel(model);

    if (tableHeader == null) {
      return;
    }

    if (getColumnCount() == 0) {
      return;
    }

    setSelectedAndIconColumnMaxWidthsToFit();
  }

  private void setSelectedAndIconColumnMaxWidthsToFit() {
    setMaxWidthToFit(convertColumnIndexToView(SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX));
    setMaxWidthToFit(convertColumnIndexToView(SelectMultipleDevicesDialogTableModel.TYPE_MODEL_COLUMN_INDEX));
  }

  private void setMaxWidthToFit(int viewColumnIndex) {
    OptionalInt maxPreferredWidth = IntStream.range(-1, getRowCount())
      .map(viewRowIndex -> getPreferredWidth(viewRowIndex, viewColumnIndex))
      .max();

    maxPreferredWidth.ifPresent(getColumnModel().getColumn(viewColumnIndex)::setMaxWidth);
  }

  private int getPreferredWidth(int viewRowIndex, int viewColumnIndex) {
    Component component;

    if (viewRowIndex == -1) {
      Object name = getColumnName(viewColumnIndex);
      component = getTableHeader().getDefaultRenderer().getTableCellRendererComponent(this, name, false, false, -1, viewColumnIndex);
    }
    else {
      component = prepareRenderer(getCellRenderer(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex);
    }

    return component.getPreferredSize().width + JBUI.scale(8);
  }

  @Override
  public void createDefaultColumnsFromModel() {
    while (columnModel.getColumnCount() != 0) {
      columnModel.removeColumn(columnModel.getColumn(0));
    }

    IntStream.range(0, dataModel.getColumnCount())
      .filter(this::notAllValuesEqualEmptyString)
      .mapToObj(TableColumn::new)
      .forEach(this::addColumn);
  }

  private boolean notAllValuesEqualEmptyString(int modelColumnIndex) {
    return !IntStream.range(0, dataModel.getRowCount())
      .mapToObj(modelRowIndex -> dataModel.getValueAt(modelRowIndex, modelColumnIndex))
      .allMatch(Predicate.isEqual(""));
  }
}
