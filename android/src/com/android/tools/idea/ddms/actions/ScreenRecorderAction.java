/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class ScreenRecorderAction extends AbstractDeviceAction {
  private final Project myProject;

  public ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context) {
    super(context,
          AndroidBundle.message("android.ddms.actions.screenrecord"),
          AndroidBundle.message("android.ddms.actions.screenrecord.description"),
          AndroidIcons.Views.VideoView);

    myProject = project;
  }

  @Override
  protected boolean isEnabled() {
    return super.isEnabled() && myDeviceContext.getSelectedDevice().supportsFeature(IDevice.Feature.SCREEN_RECORD);
  }

  @Override
  protected void performAction(@NotNull IDevice device) {
    new com.android.tools.idea.ddms.screenrecord.ScreenRecorderAction(myProject, device).performAction();
  }
}
