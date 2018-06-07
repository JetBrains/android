// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.common.actions;

import com.android.sdklib.devices.State;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Toggle orientation of the rendered device in the provided {@link DesignSurface}
 */
public class ToggleDeviceOrientationAction extends AnAction {

  private final DesignSurface mySurface;

  public ToggleDeviceOrientationAction(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Configuration configuration = mySurface.getConfiguration();
    if (configuration != null) {
      configuration.getDeviceState();
      State current = configuration.getDeviceState();
      State flip = configuration.getNextDeviceState(current);
      if (flip != null) {
        configuration.setDeviceState(flip);
      }
    }
  }
}
