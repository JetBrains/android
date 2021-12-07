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

import com.android.tools.idea.devicemanager.ActivateDeviceFileExplorerWindowValue;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceExplorerViewServiceInvokeLater;
import com.android.tools.idea.devicemanager.DeviceManagerUsageTracker;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

final class ActivateDeviceFileExplorerWindowButtonTableCellEditor extends IconButtonTableCellEditor {
  private Device myDevice;

  ActivateDeviceFileExplorerWindowButtonTableCellEditor(@NotNull Project project) {
    super(AllIcons.Actions.MenuOpen, ActivateDeviceFileExplorerWindowValue.INSTANCE, "Open this device in the Device File Explorer.");

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(DeviceManagerEvent.EventKind.PHYSICAL_DEVICE_FILE_EXPLORER_ACTION)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);
      new DeviceExplorerViewServiceInvokeLater(project).openAndShowDevice(myDevice.getKey().toString());
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
    myButton.setEnabled(myDevice.isOnline());

    return myButton;
  }
}
