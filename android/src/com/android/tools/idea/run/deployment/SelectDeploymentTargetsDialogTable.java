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
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;

final class SelectDeploymentTargetsDialogTable extends JBTable {
  SelectDeploymentTargetsDialogTable() {
    getTableHeader().setResizingAllowed(false);
    setDefaultEditor(Boolean.class, new BooleanTableCellEditor());
    setRowHeight(JBUI.scale(30));
  }

  @NotNull
  Collection<Device> getSelectedDevices() {
    SelectDeploymentTargetsDialogTableModel model = (SelectDeploymentTargetsDialogTableModel)getModel();

    return Arrays.stream(getSelectedRows())
      .map(this::convertRowIndexToModel)
      .mapToObj(model::getDeviceAt)
      .collect(Collectors.toList());
  }

  void setSelectedDevices(@NotNull Collection<Key> keys) {
    SelectDeploymentTargetsDialogTableModel model = (SelectDeploymentTargetsDialogTableModel)getModel();

    IntStream.range(0, getRowCount()).forEach(viewRowIndex -> {
      if (keys.contains(model.getDeviceAt(convertRowIndexToModel(viewRowIndex)).getKey())) {
        addRowSelectionInterval(viewRowIndex, viewRowIndex);
      }
    });
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
    setMaxWidthToFit(convertColumnIndexToView(SelectDeploymentTargetsDialogTableModel.SELECTED_MODEL_COLUMN_INDEX));
    setMaxWidthToFit(convertColumnIndexToView(SelectDeploymentTargetsDialogTableModel.ICON_MODEL_COLUMN_INDEX));
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
}
