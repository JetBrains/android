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
package com.android.tools.idea.devicemanager.groupstab.create;

import com.android.tools.idea.devicemanager.groupstab.PersistentDeviceGroups;
import com.android.tools.idea.ui.SimpleDialog;
import com.android.tools.idea.ui.SimpleDialogOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public class CreateGroupDialog {
  @NotNull private final SimpleDialog myDialog;
  @NotNull private final CreateDeviceGroupPanel myPanel;

  public CreateGroupDialog(@NotNull Project project) {
    SimpleDialogOptions options = new SimpleDialogOptions(project,
                                                          true,
                                                          DialogWrapper.IdeModalityType.PROJECT,
                                                          "Create device group",
                                                          true,
                                                          this::createCenterPanel,
                                                          () -> null,
                                                          true,
                                                          "Done",
                                                          this::handleOkAction,
                                                          "Cancel",
                                                          () -> null);
    myDialog = new SimpleDialog(options);
    myPanel = new CreateDeviceGroupPanel();
    myDialog.init();
  }

  @NotNull JComponent createCenterPanel() {
    return myPanel.getComponent();
  }

  public boolean handleOkAction() {
    PersistentDeviceGroups.getInstance().createDeviceGroup(myPanel.getNameValue(),
                                                           myPanel.getDescriptionValue(),
                                                           myPanel.getAddDevicesToGroupPanel().getGroupableDevices());
    return false;
  }

  public void show() {
    myDialog.show();
  }
}
