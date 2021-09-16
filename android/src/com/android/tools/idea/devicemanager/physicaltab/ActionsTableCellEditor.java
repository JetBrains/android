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
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.Actions;
import com.android.tools.idea.explorer.DeviceExplorerViewService;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearDevicePairingWizard;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.AbstractCellEditor;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellEditor;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private @Nullable PhysicalDevice myDevice;

  private final @NotNull PhysicalDevicePanel myPanel;
  private final @NotNull Function<@NotNull Project, @NotNull DeviceExplorerViewService> myDeviceExplorerViewServiceGetInstance;
  private final @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;
  private final @NotNull BiPredicate<@NotNull Device, @NotNull Project> myAskWithRemoveDeviceDialog;
  private final @NotNull BiFunction<@NotNull Boolean, @NotNull Boolean, @NotNull Border> myGetBorder;
  private final @NotNull ActionsComponent myComponent;

  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel) {
    this(panel,
         DeviceExplorerViewService::getInstance,
         EditDeviceNameDialog::new,
         ActionsTableCellEditor::askWithRemoveDeviceDialog,
         Tables::getBorder);
  }

  @VisibleForTesting
  ActionsTableCellEditor(@NotNull PhysicalDevicePanel panel,
                         @NotNull Function<@NotNull Project, @NotNull DeviceExplorerViewService> deviceExplorerViewServiceGetInstance,
                         @NotNull NewEditDeviceNameDialog newEditDeviceNameDialog,
                         @NotNull BiPredicate<@NotNull Device, @NotNull Project> askWithRemoveDeviceDialog,
                         @NotNull BiFunction<@NotNull Boolean, @NotNull Boolean, @NotNull Border> getBorder) {
    myPanel = panel;
    myDeviceExplorerViewServiceGetInstance = deviceExplorerViewServiceGetInstance;
    myNewEditDeviceNameDialog = newEditDeviceNameDialog;
    myAskWithRemoveDeviceDialog = askWithRemoveDeviceDialog;
    myGetBorder = getBorder;

    myComponent = new ActionsComponent();

    initActivateDeviceFileExplorerWindowButton();
    addListeners(myComponent.getEditDeviceNameButton(), event -> editDeviceName());
    addListeners(myComponent.getRemoveButton(), event -> remove());
    addListeners(myComponent.getMoreButton(), event -> showPopupMenu());
  }

  @VisibleForTesting
  static boolean askWithRemoveDeviceDialog(@NotNull Device device, @NotNull Project project) {
    return MessageDialogBuilder.okCancel("Remove " + device + " Device", device + " will be removed from the device manager.")
      .yesText("Remove")
      .ask(project);
  }

  private void initActivateDeviceFileExplorerWindowButton() {
    AbstractButton button = myComponent.getActivateDeviceFileExplorerWindowButton();
    button.setToolTipText("Open this device in the Device File Explorer.");

    addListeners(button, actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      Project project = myPanel.getProject();
      assert project != null;

      assert myDevice != null;
      myDeviceExplorerViewServiceGetInstance.apply(project).openAndShowDevice(myDevice.getKey().toString());
    });
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
    JPopupMenu menu = new JBPopupMenu();

    menu.add(newPairDeviceItem());
    // TODO(http://b/193748564) Removed until the Virtual tab menu updates its items
    // newUnpairDeviceItem().ifPresent(menu::add);

    Component button = myComponent.getMoreButton();
    menu.show(button, 0, button.getHeight());
  }

  private @NotNull JMenuItem newPairDeviceItem() {
    JMenuItem item = new JBMenuItem("Pair device");

    assert myDevice != null;
    item.setEnabled(myDevice.getType().equals(DeviceType.PHONE) && myDevice.isOnline());

    item.setToolTipText("Connect to a physical device using ADB over Wi-Fi.");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.PHYSICAL_PAIR_DEVICE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      Project project = myPanel.getProject();
      assert project != null;

      new WearDevicePairingWizard().show(project, myDevice.getKey().toString());
    });

    return item;
  }

  @SuppressWarnings("unused")
  private @NotNull Optional<@NotNull JMenuItem> newUnpairDeviceItem() {
    assert myDevice != null;
    String key = myDevice.getKey().toString();

    PhoneWearPair pair = WearPairingManager.INSTANCE.getPairedDevices(key);

    if (pair == null) {
      return Optional.empty();
    }

    PairingDevice otherDevice = pair.getPhone().getDeviceID().equals(key) ? pair.getWear() : pair.getPhone();

    JMenuItem item = new JBMenuItem("Unpair device");
    item.setToolTipText(AndroidBundle.message("wear.assistant.device.list.forget.connection", otherDevice.getDisplayName()));

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.PHYSICAL_UNPAIR_DEVICE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      try {
        BuildersKt.runBlocking(GlobalScope.INSTANCE.getCoroutineContext(),
                               (scope, continuation) -> WearPairingManager.INSTANCE.removePairedDevices(key, true, continuation));
      }
      catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        Logger.getInstance(ActionsTableCellEditor.class).warn(exception);
      }
    });

    return Optional.of(item);
  }

  private void addListeners(@NotNull AbstractButton button, @NotNull ActionListener listener) {
    button.addActionListener(listener);

    button.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        button.setBorder(UIManager.getBorder("Table.focusSelectedCellHighlightBorder"));
      }

      @Override
      public void focusLost(@NotNull FocusEvent event) {
        button.setBorder(null);
      }
    });
  }

  @NotNull ActionsComponent getComponent() {
    return myComponent;
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

    myComponent.getTableCellComponent(table, selected, false, viewRowIndex, myGetBorder);
    JComponent button = myComponent.getRemoveButton();

    String text = button.isEnabled() ? "Remove this offline device from the list." : "Connected devices can not be removed from the list.";
    button.setToolTipText(text);

    return myComponent;
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return Actions.INSTANCE;
  }
}
