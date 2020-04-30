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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Component;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ModifyDeviceSetDialog extends DialogWrapper {
  @NotNull
  private final Project myProject;

  @NotNull
  private final TableModel myTableModel;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @Nullable
  private ModifyDeviceSetDialogTable myTable;

  ModifyDeviceSetDialog(@NotNull Project project, @NotNull List<Device> devices) {
    this(project, new ModifyDeviceSetDialogTableModel(devices), DevicesSelectedService::getInstance);
  }

  @VisibleForTesting
  ModifyDeviceSetDialog(@NotNull Project project,
                        @NotNull TableModel tableModel,
                        @NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
    super(project);

    myProject = project;
    myTableModel = tableModel;
    myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;

    initTable();
    init();
    setTitle("Modify Device Set");
  }

  private void initTable() {
    myTableModel.addTableModelListener(event -> {
      if (event.getType() == TableModelEvent.UPDATE && event.getColumn() == ModifyDeviceSetDialogTableModel.SELECTED_MODEL_COLUMN_INDEX) {
        assert myTable != null;
        getOKAction().setEnabled(IntStream.range(0, myTable.getRowCount()).anyMatch(myTable::isSelected));
      }
    });

    myTable = new ModifyDeviceSetDialogTable();

    myTable.setModel(myTableModel);
    myTable.setSelectedDevices(myDevicesSelectedServiceGetInstance.apply(myProject).getDeviceKeysSelectedWithDialog());
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent panel = new JPanel();
    GroupLayout layout = new GroupLayout(panel);
    Component label = new JLabel("Available devices");
    Component scrollPane = new JBScrollPane(myTable);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction.setEnabled(false);
  }

  @NotNull
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getTable();
  }

  @NotNull
  @VisibleForTesting
  ModifyDeviceSetDialogTable getTable() {
    assert myTable != null;
    return myTable;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    assert myTable != null;
    myDevicesSelectedServiceGetInstance.apply(myProject).setDevicesSelectedWithDialog(myTable.getSelectedDevices());
  }
}
