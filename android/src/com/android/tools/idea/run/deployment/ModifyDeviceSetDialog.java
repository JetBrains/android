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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
  private static final String SELECTED_DEVICES = "SelectDeploymentTargetsDialog.selectedDevices";

  @NotNull
  private final Project myProject;

  @NotNull
  private final TableModel myTableModel;

  @Nullable
  private ModifyDeviceSetDialogTable myTable;

  ModifyDeviceSetDialog(@NotNull Project project) {
    this(project, newModifyDeviceSetDialogTableModel(project));
  }

  @NotNull
  private static TableModel newModifyDeviceSetDialogTableModel(@NotNull Project project) {
    return new ModifyDeviceSetDialogTableModel(ServiceManager.getService(project, AsyncDevicesGetter.class).get());
  }

  @VisibleForTesting
  ModifyDeviceSetDialog(@NotNull Project project, @NotNull TableModel tableModel) {
    super(project);

    myProject = project;
    myTableModel = tableModel;

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
    myTable.setSelectedDevices(getSelectedKeys(myProject));
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

    String[] keys = myTable.getSelectedDevices().stream()
      .map(Device::getKey)
      .map(Key::toString)
      .toArray(String[]::new);

    PropertiesComponent.getInstance(myProject).setValues(SELECTED_DEVICES, keys);
  }

  @NotNull
  static List<Device> getSelectedDevices(@NotNull Project project) {
    Collection<Device> devices = ServiceManager.getService(project, AsyncDevicesGetter.class).get();
    Collection<Key> keys = getSelectedKeys(project);

    return ContainerUtil.filter(devices, device -> keys.contains(device.getKey()));
  }

  @NotNull
  private static Collection<Key> getSelectedKeys(@NotNull Project project) {
    String[] keys = PropertiesComponent.getInstance(project).getValues(SELECTED_DEVICES);

    if (keys == null) {
      return Collections.emptySet();
    }

    return Arrays.stream(keys)
      .map(Key::new)
      .collect(Collectors.toSet());
  }
}
