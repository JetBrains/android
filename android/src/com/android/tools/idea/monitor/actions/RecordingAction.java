/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.monitor.actions;

import com.android.tools.idea.monitor.DeviceSampler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class RecordingAction extends ToggleAction {
  @NotNull private final DeviceSampler myDeviceSampler;

  public RecordingAction(@NotNull DeviceSampler deviceSampler) {
    super(null, null, AllIcons.Actions.Pause);
    myDeviceSampler = deviceSampler;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return !myDeviceSampler.isRunning();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (isSelected(e)) {
      presentation.setText("Pause");
      presentation.setDescription("Pauses " + myDeviceSampler.getDescription() + " recording.");
    }
    else {
      presentation.setText("Resume");
      presentation.setDescription("Resumes " + myDeviceSampler.getDescription() + " recording.");
    }
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (state) {
      myDeviceSampler.stop();
    }
    else {
      myDeviceSampler.start();
    }
  }
}
