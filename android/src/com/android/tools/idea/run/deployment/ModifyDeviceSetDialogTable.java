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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

final class ModifyDeviceSetDialogTable extends JBTable {
  ModifyDeviceSetDialogTable() {
    getTableHeader().setResizingAllowed(false);
    setDefaultEditor(Boolean.class, new BooleanTableCellEditor());
    setRowHeight(JBUI.scale(30));
  }

  @NotNull
  Collection<Device> getSelectedDevices() {
    ModifyDeviceSetDialogTableModel model = (ModifyDeviceSetDialogTableModel)getModel();

    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .mapToObj(model::getDeviceAt)
      .collect(Collectors.toList());
  }

  void setSelectedDevices(@NotNull Collection<Key> keys) {
    ModifyDeviceSetDialogTableModel model = (ModifyDeviceSetDialogTableModel)getModel();

    IntStream.range(0, getRowCount()).forEach(viewRowIndex -> {
      if (keys.contains(model.getDeviceAt(convertRowIndexToModel(viewRowIndex)).getKey())) {
        addRowSelectionInterval(viewRowIndex, viewRowIndex);
      }
    });
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

    setSelectedAndIconColumnMaxWidthsToFit();
  }

  private void setSelectedAndIconColumnMaxWidthsToFit() {
    setMaxWidthToFit(convertColumnIndexToView(ModifyDeviceSetDialogTableModel.SELECTED_MODEL_COLUMN_INDEX));
    setMaxWidthToFit(convertColumnIndexToView(ModifyDeviceSetDialogTableModel.TYPE_MODEL_COLUMN_INDEX));
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
