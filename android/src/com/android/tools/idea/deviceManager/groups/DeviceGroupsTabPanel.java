/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.groups;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import java.util.Collections;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

public class DeviceGroupsTabPanel {
  private final @NotNull Project myProject;
  private @NotNull JPanel myRootComponent;
  private @NotNull DeviceGroupsToolbarPanel myDeviceGroupsToolbarPanel;

  private @NotNull JTable myGroupsTable;

  public DeviceGroupsTabPanel(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull JPanel getComponent() {
    return myRootComponent;
  }

  private void createUIComponents() {
    myDeviceGroupsToolbarPanel = new DeviceGroupsToolbarPanel(myProject);

    // Hardcode for now
    DeviceGroup exampleGroup = new DeviceGroup("Example Group", "My group of devices");
    // TODO(b/174518417): call this on a background thread
    for (AvdInfo avdInfo : AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(true)) {
      exampleGroup.getDevices().add(new GroupableDevice(avdInfo));
    }
    DeviceGroupsTableModel groupsTableModel = new DeviceGroupsTableModel(Collections.singletonList(exampleGroup));

    myGroupsTable = new JBTable(groupsTableModel);
    myGroupsTable.setDefaultRenderer(DeviceGroup.class, new DeviceGroupTableCellRenderer());
  }
}
