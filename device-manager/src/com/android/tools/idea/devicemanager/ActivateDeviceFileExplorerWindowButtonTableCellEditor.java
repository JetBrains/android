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

import com.google.wireless.android.sdk.stats.DeviceManagerEvent;
import com.google.wireless.android.sdk.stats.DeviceManagerEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActivateDeviceFileExplorerWindowButtonTableCellEditor<D extends Device> extends IconButtonTableCellEditor {
  private Device myDevice;

  private final @Nullable Object myProject;
  private final @NotNull DeviceTable<@NotNull D> myTable;

  public ActivateDeviceFileExplorerWindowButtonTableCellEditor(@Nullable Project project,
                                                               @NotNull DeviceTable<@NotNull D> table,
                                                               @NotNull EventKind kind) {
    super(AllIcons.Actions.MenuOpen, ActivateDeviceFileExplorerWindowValue.INSTANCE, "Open this device in the Device File Explorer.");

    myProject = project;
    myTable = table;

    myButton.addActionListener(actionEvent -> {
      DeviceManagerEvent deviceManagerEvent = DeviceManagerEvent.newBuilder()
        .setKind(kind)
        .build();

      DeviceManagerUsageTracker.log(deviceManagerEvent);

      assert project != null;
      new DeviceExplorerViewServiceInvokeLater(project).openAndShowDevice(myDevice);
    });
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    super.getTableCellEditorComponent(table, value, selected, viewRowIndex, viewColumnIndex);

    if (myProject == null) {
      myButton.setEnabled(false);
    }
    else {
      myDevice = myTable.getDeviceAt(viewRowIndex);
      myButton.setEnabled(myDevice.isOnline());
    }

    return myButton;
  }
}
