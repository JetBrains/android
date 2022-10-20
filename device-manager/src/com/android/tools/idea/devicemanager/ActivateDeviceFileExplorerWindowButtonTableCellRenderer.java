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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActivateDeviceFileExplorerWindowButtonTableCellRenderer<D extends Device> extends IconButtonTableCellRenderer {
  private final @Nullable Object myProject;
  private final @NotNull DeviceTable<@NotNull D> myTable;

  public ActivateDeviceFileExplorerWindowButtonTableCellRenderer(@Nullable Project project, @NotNull DeviceTable<@NotNull D> table) {
    super(AllIcons.Actions.MenuOpen,
          StudioFlags.MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED.get()
            ? "Open this device in the Device Explorer."
            : "Open this device in the Device File Explorer.");

    myProject = project;
    myTable = table;
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    super.getTableCellRendererComponent(table, value, selected, focused, viewRowIndex, viewColumnIndex);
    myButton.setEnabled(myProject != null && myTable.getDeviceAt(viewRowIndex).isOnline());

    return myButton;
  }
}
