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
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerViewService;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.awt.Component;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.swing.AbstractCellEditor;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private @Nullable PhysicalDevice myDevice;

  private final @NotNull PhysicalDevicePanel myPanel;
  private final @NotNull Function<@NotNull Project, @NotNull DeviceExplorerViewService> myDeviceExplorerViewServiceGetInstance;
  private final @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;
  private final @NotNull BiPredicate<@NotNull Device, @NotNull Project> myAskWithRemoveDeviceDialog;
  private final @NotNull ActionsComponent myComponent;

  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel) {
    this(panel,
         DeviceExplorerViewService::getInstance,
         EditDeviceNameDialog::new,
         ActionsTableCellEditor::askWithRemoveDeviceDialog);
  }

  @VisibleForTesting
  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel,
                         @NotNull Function<@NotNull Project, @NotNull DeviceExplorerViewService> deviceExplorerViewServiceGetInstance,
                         @NotNull NewEditDeviceNameDialog newEditDeviceNameDialog,
                         @NotNull BiPredicate<@NotNull Device, @NotNull Project> askWithRemoveDeviceDialog) {
    myPanel = panel;
    myDeviceExplorerViewServiceGetInstance = deviceExplorerViewServiceGetInstance;
    myNewEditDeviceNameDialog = newEditDeviceNameDialog;
    myAskWithRemoveDeviceDialog = askWithRemoveDeviceDialog;

    myComponent = new ActionsComponent();

    myComponent.getActivateDeviceFileExplorerWindowButton().addActionListener(event -> activateDeviceFileExplorerWindow());
    myComponent.getEditDeviceNameButton().addActionListener(event -> editDeviceName());
    myComponent.getRemoveButton().addActionListener(event -> remove());
    myComponent.getMoreButton().addActionListener(event -> showPopupMenu());
  }

  @VisibleForTesting
  static boolean askWithRemoveDeviceDialog(@NotNull Device device, @NotNull Project project) {
    return MessageDialogBuilder.okCancel("Remove " + device + " Device", device + " will be removed from the device manager.")
      .yesText("Remove")
      .ask(project);
  }

  private void activateDeviceFileExplorerWindow() {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);

    Project project = myPanel.getProject();
    assert project != null;

    assert myDevice != null;
    myDeviceExplorerViewServiceGetInstance.apply(project).openAndShowDevice(myDevice.getKey().toString());
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
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.PHYSICAL_DELETE_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);
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

  private void showPopupMenu() {
    JMenuItem item = new JBMenuItem("Pair device");

    assert myDevice != null;
    item.setEnabled(myDevice.isOnline() && myDevice.isPhoneOrTablet());

    item.addActionListener(event -> pairDevice());

    JPopupMenu menu = new JBPopupMenu();
    menu.add(item);

    Component button = myComponent.getMoreButton();
    menu.show(button, 0, button.getHeight());
  }

  private void pairDevice() {
    DeviceManagerEvent event = DeviceManagerEvent.newBuilder()
      .setKind(EventKind.PHYSICAL_PAIR_DEVICE_ACTION)
      .build();

    DeviceManagerUsageTracker.log(event);
    // TODO Pair device
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
    myDevice = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex);
    return myComponent.getTableCellComponent(table, selected, true, viewRowIndex);
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return Actions.INSTANCE;
  }
}
