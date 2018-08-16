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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public final class ScreenRecorderAction extends AbstractDeviceAction {
  private final Features myFeatures;
  private final Project myProject;

  public ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context) {
    this(project, context, new CachedFeatures(project));
  }

  @VisibleForTesting
  ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context, @NotNull Features features) {
    super(context, AndroidBundle.message("android.ddms.actions.screenrecord"),
          AndroidBundle.message("android.ddms.actions.screenrecord.description"), AndroidIcons.Ddms.ScreenRecorder);

    myFeatures = features;
    myProject = project;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (!isEnabled()) {
      presentation.setEnabled(false);
      presentation.setText(AndroidBundle.message("android.ddms.actions.screenrecord"));

      return;
    }

    IDevice device = myDeviceContext.getSelectedDevice();

    if (myFeatures.watch(device)) {
      presentation.setEnabled(false);
      presentation.setText("Screen Record Is Unavailable for Wear OS");

      return;
    }

    presentation.setEnabled(myFeatures.screenRecord(device));
    presentation.setText(AndroidBundle.message("android.ddms.actions.screenrecord"));
  }

  @Override
  protected void performAction(@NotNull IDevice device) {
    new com.android.tools.idea.ddms.screenrecord.ScreenRecorderAction(myProject, device, myFeatures.screenRecord(device)).performAction();
  }
}
