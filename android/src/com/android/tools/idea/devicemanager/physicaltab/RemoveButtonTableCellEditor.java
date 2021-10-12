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
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTableModel.RemoveValue;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.MessageDialogBuilder;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class RemoveButtonTableCellEditor extends IconButtonTableCellEditor {
  private final @NotNull PhysicalDevicePanel myPanel;
  private Device myDevice;

  RemoveButtonTableCellEditor(@NotNull PhysicalDevicePanel panel) {
    super(AllIcons.Actions.GC, RemoveValue.INSTANCE);
    myPanel = panel;

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.PHYSICAL_DELETE_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      String message = myDevice + " will be removed from the device manager.";

      boolean remove = MessageDialogBuilder.okCancel("Remove " + myDevice + " Device", message)
        .yesText("Remove")
        .ask(myPanel.getProject());

      if (!remove) {
        fireEditingCanceled();
        return;
      }

      fireEditingStopped();
      myPanel.getTable().getModel().remove(myDevice.getKey());
    });
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);

    myDevice = ((PhysicalDeviceTable)table).getDeviceAt(viewRowIndex);
    boolean online = myDevice.isOnline();

    myButton.setEnabled(!online);
    myButton.setToolTipText(online ? "Connected devices can not be removed from the list." : "Remove this offline device from the list.");

    return myButton;
  }
}
