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

import com.intellij.openapi.project.Project;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class DeviceGroupsTabPanel {
  @NotNull private final Project myProject;
  @NotNull private JPanel myRootComponent;
  @NotNull private DeviceGroupsToolbarPanel myDeviceGroupsToolbarPanel;

  public DeviceGroupsTabPanel(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }

  private void createUIComponents() {
    myDeviceGroupsToolbarPanel = new DeviceGroupsToolbarPanel(myProject);
  }
}
