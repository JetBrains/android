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
package com.android.tools.idea.devicemanager.groupstab;

import com.android.tools.idea.devicemanager.groupstab.create.CreateGroupDialog;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class DeviceGroupsToolbarPanel {
  @NotNull private JPanel myRootComponent;
  @NotNull private JButton myCreateGroupButton; // TODO: add this
  @NotNull private JButton myRefreshButton; // TODO: add this
  @NotNull private JButton myHelpButton;

  public DeviceGroupsToolbarPanel(@NotNull Project project) {
    myHelpButton.addActionListener(
      e -> BrowserUtil.browse("http://developer.android.com/r/studio-ui/virtualdeviceconfig.html")); // TODO: Change target URL

    myCreateGroupButton.addActionListener(
      e -> {
        CreateGroupDialog dialog = new CreateGroupDialog(project);
        dialog.show();
      });
  }

  @NotNull
  public JPanel getComponent() {
    return myRootComponent;
  }
}
