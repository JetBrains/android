/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import java.util.function.BiConsumer;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private @Nullable PhysicalDevice myDevice;

  private final @NotNull Project myProject;
  private final @NotNull PhysicalDeviceTableModel myModel;
  private final @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice;
  private final @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;
  private final @NotNull ActionsComponent myComponent;

  ActionsTableCellEditor(@NotNull Project project, @NotNull PhysicalDeviceTableModel model) {
    this(project, model, DeviceExplorerToolWindowFactory::openAndShowDevice, EditDeviceNameDialog::new);
  }

  @VisibleForTesting
  ActionsTableCellEditor(@NotNull Project project,
                         @NotNull PhysicalDeviceTableModel model,
                         @NotNull BiConsumer<@NotNull Project, @NotNull String> openAndShowDevice,
                         @NotNull NewEditDeviceNameDialog newEditDeviceNameDialog) {
    myProject = project;
    myModel = model;
    myOpenAndShowDevice = openAndShowDevice;
    myNewEditDeviceNameDialog = newEditDeviceNameDialog;

    myComponent = new ActionsComponent();

    myComponent.getActivateDeviceFileExplorerWindowButton().addActionListener(event -> activateDeviceFileExplorerWindow());
    myComponent.getEditDeviceNameButton().addActionListener(event -> editDeviceName());
  }

  private void activateDeviceFileExplorerWindow() {
    assert myDevice != null;
    myOpenAndShowDevice.accept(myProject, myDevice.getKey().toString());
  }

  private void editDeviceName() {
    assert myDevice != null;
    EditDeviceNameDialog dialog = myNewEditDeviceNameDialog.apply(myProject, myDevice.getNameOverride(), myDevice.getName());

    if (!dialog.showAndGet()) {
      return;
    }

    myModel.setNameOverride(myDevice.getKey(), dialog.getNameOverride());
  }

  @VisibleForTesting
  Object getDevice() {
    return myDevice;
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    viewColumnIndex = table.convertColumnIndexToView(PhysicalDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
    myDevice = (PhysicalDevice)table.getValueAt(viewRowIndex, viewColumnIndex);

    myComponent.getActivateDeviceFileExplorerWindowButton().setEnabled(myDevice.isOnline());

    myComponent.setBackground(Tables.getBackground(table, selected));
    myComponent.setBorder(Tables.getBorder(selected, true));

    return myComponent;
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return Actions.INSTANCE;
  }
}
