/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.MenuItems;
import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import com.android.tools.idea.devicemanager.legacy.LegacyAvdManagerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class VirtualDevicePopUpMenuButtonTableCellEditor extends PopUpMenuButtonTableCellEditor {
  private final @NotNull Emulator myEmulator;
  private VirtualDevice myDevice;

  VirtualDevicePopUpMenuButtonTableCellEditor(@NotNull VirtualDevicePanel panel) {
    this(panel, new Emulator());
  }

  @VisibleForTesting
  VirtualDevicePopUpMenuButtonTableCellEditor(@NotNull VirtualDevicePanel panel, @NotNull Emulator emulator) {
    super(panel);
    myEmulator = emulator;
  }

  @NotNull VirtualDevicePanel getPanel() {
    return (VirtualDevicePanel)myPanel;
  }

  @NotNull VirtualDevice getDevice() {
    return myDevice;
  }

  @Override
  public @NotNull List<@NotNull JComponent> newItems() {
    List<JComponent> items = new ArrayList<>();

    items.add(newDuplicateItem());
    items.add(new WipeDataItem(this));
    newColdBootNowItem().ifPresent(items::add);
    items.add(newShowOnDiskItem());
    items.add(MenuItems.newViewDetailsItem(myPanel));
    items.add(new Separator());
    newUnpairDeviceItem(myDevice.getKey(), EventKind.VIRTUAL_UNPAIR_DEVICE_ACTION).ifPresent(items::add);
    items.add(new DeleteItem(this));

    return items;
  }

  private @NotNull JComponent newDuplicateItem() {
    AbstractButton item = new JBMenuItem("Duplicate");
    item.setToolTipText("Duplicate this AVD");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_DUPLICATE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      VirtualDeviceTable table = getPanel().getTable();

      if (AvdWizardUtils.createAvdWizardForDuplication(table, myPanel.getProject(), myDevice.getAvdInfo()).showAndGet()) {
        table.refreshAvds();
      }
    });

    return item;
  }

  private @NotNull Optional<@NotNull JComponent> newColdBootNowItem() {
    if (!myEmulator.supportsColdBooting()) {
      return Optional.empty();
    }

    AbstractButton item = new JBMenuItem("Cold Boot Now");
    item.setToolTipText("Force one cold boot");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_COLD_BOOT_NOW_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      Project project = myPanel.getProject();

      Futures.addCallback(AvdManagerConnection.getDefaultAvdManagerConnection().startAvdWithColdBoot(project, myDevice.getAvdInfo()),
                          LegacyAvdManagerUtils.newCallback(project),
                          EdtExecutorService.getInstance());
    });

    return Optional.of(item);
  }

  private @NotNull JComponent newShowOnDiskItem() {
    AbstractButton item = new JBMenuItem("Show on Disk");
    item.setToolTipText("Open the location of this AVD's data files");

    item.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.VIRTUAL_SHOW_ON_DISK_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      RevealFileAction.openDirectory(myDevice.getAvdInfo().getDataFolderPath());
    });

    return item;
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);
    myDevice = ((VirtualDeviceTable)table).getDeviceAt(viewRowIndex);

    return myButton;
  }
}
