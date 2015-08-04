/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;

// TODO: Remove. Having a mode like this probably isn't a good idea.
public class PushToDeviceAction extends ToggleAction {
  public PushToDeviceAction() {
    super("Fast Deployment Mode: Push Changes To Running App Instantly", null, AndroidIcons.PushFileToDevice);
  }

  private boolean mSelected;

  @Override
  public boolean isSelected(AnActionEvent e) {
    return mSelected;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    mSelected = state;

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      FastDeployManager manager = project.getComponent(FastDeployManager.class);
      manager.setActive(state);
    }
  }
}
