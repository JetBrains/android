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

import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SelectMultipleDevicesDialog extends DialogWrapper {
  @NotNull
  private final Project myProject;

  private final @NotNull List<Device> myDevices;
  private final @NotNull TableModel myModel;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @Nullable
  private SelectMultipleDevicesDialogTable myTable;

  SelectMultipleDevicesDialog(@NotNull Project project, @NotNull List<Device> devices) {
    this(project,
         devices,
         StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED::get,
         DevicesSelectedService::getInstance);
  }

  @VisibleForTesting
  SelectMultipleDevicesDialog(@NotNull Project project,
                              @NotNull List<Device> devices,
                              @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet,
                              @NotNull Function<Project, DevicesSelectedService> devicesSelectedServiceGetInstance) {
    super(project);

    myProject = project;
    myDevices = devices;
    myModel = new SelectMultipleDevicesDialogTableModel(devices, selectDeviceSnapshotComboBoxSnapshotsEnabledGet);
    myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;

    initTable();
    init();
    setTitle("Select Multiple Devices");
  }

  private void initTable() {
    myTable = new SelectMultipleDevicesDialogTable();

    myTable.setModel(myModel);
    myTable.setSelectedTargets(myDevicesSelectedServiceGetInstance.apply(myProject).getTargetsSelectedWithDialog(myDevices));
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent panel = new JPanel();
    GroupLayout layout = new GroupLayout(panel);
    Component label = new JLabel("Available devices");

    Component scrollPane = new JBScrollPane(myTable);
    scrollPane.setPreferredSize(JBUI.size(556, 270));

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
  protected void doOKAction() {
    super.doOKAction();

    assert myTable != null;
    myDevicesSelectedServiceGetInstance.apply(myProject).setTargetsSelectedWithDialog(myTable.getSelectedTargets());
  }

  @VisibleForTesting
  @Override
  @SuppressWarnings("EmptyMethod")
  protected @NotNull Action getOKAction() {
    return super.getOKAction();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  protected @NotNull String getDimensionServiceKey() {
    return "com.android.tools.idea.run.deployment.SelectMultipleDevicesDialog";
  }

  @NotNull
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getTable();
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    assert myTable != null;
    Collection<Target> targets = myTable.getSelectedTargets();

    Collection<Key> keys = Sets.newHashSetWithExpectedSize(targets.size());

    boolean duplicateKeys = targets.stream()
      .map(Target::getDeviceKey)
      .anyMatch(key -> !keys.add(key));

    if (duplicateKeys) {
      String message = "Some of the selected targets are for the same device. Each target should be for a different device.";
      return new ValidationInfo(message, null);
    }

    return null;
  }

  @VisibleForTesting
  @NotNull SelectMultipleDevicesDialogTable getTable() {
    assert myTable != null;
    return myTable;
  }
}
