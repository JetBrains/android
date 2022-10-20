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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.device.explorer.DeviceExplorerWrapper;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevice;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorer;
import com.android.tools.idea.flags.StudioFlags;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActivateDeviceFileExplorerWindowButtonTableCellEditor<D extends Device> extends IconButtonTableCellEditor {
  private Device myDevice;

  private final @Nullable Project myProject;
  private final @NotNull DeviceTable<@NotNull D> myTable;

  public ActivateDeviceFileExplorerWindowButtonTableCellEditor(@Nullable Project project,
                                                               @NotNull DeviceTable<@NotNull D> table,
                                                               @NotNull EventKind kind) {
    super(ActivateDeviceFileExplorerWindowValue.INSTANCE,
          AllIcons.Actions.MenuOpen,
          StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get()
            ? "Open this device in the Device Explorer."
            : "Open this device in the Device File Explorer.");

    myProject = project;
    myTable = table;

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(kind)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      assert project != null;
      openAndShowDeviceLater();
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
    myDevice = myTable.getDeviceAt(viewRowIndex);

    return myButton;
  }

  /**
   * Shows DeviceExplorer for the given physical or virtual device.
   */
  private void openAndShowDeviceLater() {
    // We need to use an invokeLater to avoid a NPE, for convoluted
    // reasons documented in b/200165926.
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject != null && !myProject.isDisposed()) {
        if (myDevice instanceof VirtualDevice) {
          if (StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get()) {
            DeviceExplorerWrapper.openAndShowDevice(myProject, ((VirtualDevice)myDevice).getAvdInfo());
          } else {
            DeviceExplorer.openAndShowDevice(myProject, ((VirtualDevice)myDevice).getAvdInfo());
          }
        }
        else if (myDevice instanceof PhysicalDevice) {
          if (StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get()) {
            DeviceExplorerWrapper.openAndShowDevice(myProject, myDevice.getKey().toString());
          } else {
            DeviceExplorer.openAndShowDevice(myProject, myDevice.getKey().toString());
          }
        }
        else {
          assert false;
        }
      }
    });
  }
}
