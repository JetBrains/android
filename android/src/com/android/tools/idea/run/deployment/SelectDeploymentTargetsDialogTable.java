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

import com.intellij.ui.table.JBTable;
import java.util.Arrays;
import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.event.TableModelEvent;
import org.jetbrains.annotations.NotNull;

final class SelectDeploymentTargetsDialogTable extends JBTable {
  SelectDeploymentTargetsDialogTable(@NotNull SelectDeploymentTargetsDialogTableModel model) {
    super(model);

    setDefaultEditor(Boolean.class, new BooleanTableCellEditor());
    setTableHeader(null);

    model.addTableModelListener(this::synchronizeRowSelectionWithSelectedColumn);
    model.addTableModelListener(event -> setSelectedAndIconColumnMaxWidthsToFit());

    getSelectionModel().addListSelectionListener(event -> synchronizeSelectedColumnWithRowSelection());
  }

  private void synchronizeRowSelectionWithSelectedColumn(@NotNull TableModelEvent event) {
    int modelColumnIndex = event.getColumn();

    if (modelColumnIndex != SelectDeploymentTargetsDialogTableModel.SELECTED_MODEL_COLUMN_INDEX) {
      return;
    }

    int viewRowIndex = convertRowIndexToView(event.getFirstRow());
    int viewColumnIndex = convertColumnIndexToView(modelColumnIndex);

    if ((boolean)getValueAt(viewRowIndex, viewColumnIndex)) {
      getSelectionModel().addSelectionInterval(viewRowIndex, viewRowIndex);
      return;
    }

    getSelectionModel().removeSelectionInterval(viewRowIndex, viewRowIndex);
  }

  private void setSelectedAndIconColumnMaxWidthsToFit() {
    setMaxWidthToFit(convertColumnIndexToView(SelectDeploymentTargetsDialogTableModel.SELECTED_MODEL_COLUMN_INDEX));
    setMaxWidthToFit(convertColumnIndexToView(SelectDeploymentTargetsDialogTableModel.ICON_MODEL_COLUMN_INDEX));
  }

  private void setMaxWidthToFit(int viewColumnIndex) {
    OptionalInt maxPreferredWidth = IntStream.range(0, getRowCount())
      .map(viewRowIndex -> getPreferredWidth(viewRowIndex, viewColumnIndex))
      .max();

    maxPreferredWidth.ifPresent(getColumnModel().getColumn(viewColumnIndex)::setMaxWidth);
  }

  private int getPreferredWidth(int viewRowIndex, int viewColumnIndex) {
    return prepareRenderer(getCellRenderer(viewRowIndex, viewColumnIndex), viewRowIndex, viewColumnIndex).getPreferredSize().width + 1;
  }

  private void synchronizeSelectedColumnWithRowSelection() {
    SelectDeploymentTargetsDialogTableModel model = (SelectDeploymentTargetsDialogTableModel)getModel();

    IntStream.range(0, getRowCount())
      .forEach(viewRowIndex -> model.setSelected(isRowSelected(viewRowIndex), convertRowIndexToModel(viewRowIndex)));
  }

  @NotNull
  Collection<Device> getSelectedDevices() {
    SelectDeploymentTargetsDialogTableModel model = (SelectDeploymentTargetsDialogTableModel)getModel();

    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .mapToObj(model::getDeviceAt)
      .collect(Collectors.toList());
  }
}
