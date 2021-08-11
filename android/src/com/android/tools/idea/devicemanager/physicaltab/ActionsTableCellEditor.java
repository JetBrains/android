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

import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.awt.Component;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private @Nullable PhysicalDevice myDevice;

  private final @NotNull PhysicalDevicePanel myPanel;
  private final @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice;
  private final @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;
  private final @NotNull BiPredicate<@NotNull Device, @NotNull Project> myAskWithRemoveDeviceDialog;
  private final @NotNull ActionsComponent myComponent;

  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel) {
    this(panel,
         DeviceExplorerToolWindowFactory::openAndShowDevice,
         EditDeviceNameDialog::new,
         ActionsTableCellEditor::askWithRemoveDeviceDialog);
  }

  @VisibleForTesting
  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel,
                         @NotNull BiConsumer<@NotNull Project, @NotNull String> openAndShowDevice,
                         @NotNull NewEditDeviceNameDialog newEditDeviceNameDialog,
                         @NotNull BiPredicate<@NotNull Device, @NotNull Project> askWithRemoveDeviceDialog) {
    myPanel = panel;
    myOpenAndShowDevice = openAndShowDevice;
    myNewEditDeviceNameDialog = newEditDeviceNameDialog;
    myAskWithRemoveDeviceDialog = askWithRemoveDeviceDialog;

    myComponent = new ActionsComponent();

    myComponent.getActivateDeviceFileExplorerWindowButton().addActionListener(event -> activateDeviceFileExplorerWindow());
    myComponent.getEditDeviceNameButton().addActionListener(event -> editDeviceName());
    myComponent.getRemoveButton().addActionListener(event -> remove());
  }

  @VisibleForTesting
  static boolean askWithRemoveDeviceDialog(@NotNull Device device, @NotNull Project project) {
    return MessageDialogBuilder.okCancel("Remove " + device + " Device", device + " will be removed from the device manager.")
      .yesText("Remove")
      .ask(project);
  }

  private void activateDeviceFileExplorerWindow() {
    Project project = myPanel.getProject();
    assert project != null;

    assert myDevice != null;
    myOpenAndShowDevice.accept(project, myDevice.getKey().toString());
  }

  private void editDeviceName() {
    assert myDevice != null;
    EditDeviceNameDialog dialog = myNewEditDeviceNameDialog.apply(myPanel.getProject(), myDevice.getNameOverride(), myDevice.getName());

    if (!dialog.showAndGet()) {
      return;
    }

    myPanel.getTable().getModel().setNameOverride(myDevice.getKey(), dialog.getNameOverride());
  }

  private void remove() {
    assert myDevice != null;

    Project project = myPanel.getProject();
    assert project != null;

    if (!myAskWithRemoveDeviceDialog.test(myDevice, project)) {
      fireEditingCanceled();
      return;
    }

    fireEditingStopped();
    myPanel.getTable().getModel().remove(myDevice.getKey());
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

    boolean online = myDevice.isOnline();

    myComponent.getActivateDeviceFileExplorerWindowButton().setEnabled(online);
    myComponent.getRemoveButton().setEnabled(!online);

    myComponent.setBackground(Tables.getBackground(table, selected));
    myComponent.setBorder(Tables.getBorder(selected, true));

    return myComponent;
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return Actions.INSTANCE;
  }
}
