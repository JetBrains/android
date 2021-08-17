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
import com.intellij.CommonBundle;
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
import java.util.stream.IntStream;
import javax.swing.Action;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SelectMultipleDevicesDialog extends DialogWrapper {
  @NotNull
  private final Project myProject;

  private final @NotNull List<@NotNull Device> myDevices;
  private final @NotNull BooleanSupplier myRunOnMultipleDevicesActionEnabledGet;
  private final @NotNull TableModel myModel;

  @NotNull
  private final Function<Project, DevicesSelectedService> myDevicesSelectedServiceGetInstance;

  @Nullable
  private SelectMultipleDevicesDialogTable myTable;

  SelectMultipleDevicesDialog(@NotNull Project project, @NotNull List<Device> devices) {
    this(project,
         devices,
         StudioFlags.RUN_ON_MULTIPLE_DEVICES_ACTION_ENABLED::get,
         StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED::get,
         DevicesSelectedService::getInstance);
  }

  @VisibleForTesting
  SelectMultipleDevicesDialog(@NotNull Project project,
                              @NotNull List<@NotNull Device> devices,
                              @NotNull BooleanSupplier runOnMultipleDevicesActionEnabledGet,
                              @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet,
                              @NotNull Function<@NotNull Project, @NotNull DevicesSelectedService> devicesSelectedServiceGetInstance) {
    super(project);

    myProject = project;
    myDevices = devices;
    myRunOnMultipleDevicesActionEnabledGet = runOnMultipleDevicesActionEnabledGet;
    myModel = new SelectMultipleDevicesDialogTableModel(devices, selectDeviceSnapshotComboBoxSnapshotsEnabledGet);
    myDevicesSelectedServiceGetInstance = devicesSelectedServiceGetInstance;

    initTable();
    initOkAction();
    init();
    setTitle(runOnMultipleDevicesActionEnabledGet.getAsBoolean() ? "Run on Multiple Devices" : "Select Multiple Devices");
  }

  private void initTable() {
    if (myRunOnMultipleDevicesActionEnabledGet.getAsBoolean()) {
      myModel.addTableModelListener(event -> {
        if (event.getColumn() == SelectMultipleDevicesDialogTableModel.SELECTED_MODEL_COLUMN_INDEX &&
            event.getType() == TableModelEvent.UPDATE) {
          assert myTable != null;
          getOKAction().setEnabled(IntStream.range(0, myTable.getRowCount()).anyMatch(myTable::isSelected));
        }
      });
    }

    myTable = new SelectMultipleDevicesDialogTable();

    myTable.setModel(myModel);
    myTable.setSelectedTargets(myDevicesSelectedServiceGetInstance.apply(myProject).getTargetsSelectedWithDialog(myDevices));
  }

  private void initOkAction() {
    // Undo what happened in createDefaultActions below if we're using the new multiple devices UI
    if (!myRunOnMultipleDevicesActionEnabledGet.getAsBoolean()) {
      myOKAction.setEnabled(true);
      myOKAction.putValue(Action.NAME, CommonBundle.getOkButtonText());
    }
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

  // TODO Remove this when we remove StudioFlags.RUN_ON_MULTIPLE_DEVICES_ACTION_ENABLED
  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();

    // Default to the old multiple devices UI. I'd do that depending on the myRunOnMultipleDevicesActionEnabledGet field (and not have
    // initOkAction) but the field is null at this point because this method is called by DialogWrapper before the field is initialized. I
    // could suck it up and statically reference the flag instead but I still think that mutating static state in tests is the greater evil.
    myOKAction.setEnabled(false);
    myOKAction.putValue(Action.NAME, "Run");
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
