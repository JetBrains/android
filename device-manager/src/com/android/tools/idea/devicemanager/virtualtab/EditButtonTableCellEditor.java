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

import com.android.tools.idea.avdmanager.AvdWizardUtils;
import com.android.tools.idea.devicemanager.DeviceManagerFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.android.tools.idea.devicemanager.IconButtonTableCellEditor;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.EditValue;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class EditButtonTableCellEditor extends IconButtonTableCellEditor {
  private VirtualDevice myDevice;

  EditButtonTableCellEditor(@NotNull VirtualDevicePanel panel) {
    super(EditValue.INSTANCE, AllIcons.Actions.Edit, "Edit this AVD");

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(EventKind.VIRTUAL_EDIT_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      VirtualDeviceTable table = panel.getTable();

      if (AvdWizardUtils.createAvdWizard(table, panel.getProject(), myDevice.getAvdInfo()).showAndGet()) {
        FutureCallback<Key> callback = new DeviceManagerFutureCallback<>(EditButtonTableCellEditor.class, table::setSelectedDevice);
        Futures.addCallback(table.reloadDevice(myDevice.getKey()), callback, EdtExecutorService.getInstance());
      }

      fireEditingCanceled();
    });
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
