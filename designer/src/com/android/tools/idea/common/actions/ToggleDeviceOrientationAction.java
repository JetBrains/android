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

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.actions.DesignerActions;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlSupportedActions;
import com.android.tools.idea.uibuilder.surface.NlSupportedActionsKt;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Toggle orientation of the rendered device in the provided {@link DesignSurface}
 */
public class ToggleDeviceOrientationAction extends AnAction {

  private ToggleDeviceOrientationAction() {
  }

  @NotNull
  public static ToggleDeviceOrientationAction getInstance() {
    return (ToggleDeviceOrientationAction) ActionManager.getInstance().getAction(DesignerActions.ACTION_TOGGLE_DEVICE_ORIENTATION);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (DesignerActionUtils.isActionEventFromJTextField(e)) {
      e.getPresentation().setEnabled(false);
      return;
    }
    DesignSurface<?> surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface != null && surface.getConfigurations().stream().allMatch(config -> Device.isWear(config.getDevice()))) {
      // If all devices are wear device, disable this action because the orientation is fixed for wear devices.
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(NlSupportedActionsKt.isActionSupported(surface, NlSupportedActions.SWITCH_DEVICE_ORIENTATION));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface == null) return;
    surface.getConfigurations()
      .forEach(configuration -> {
        if (Device.isWear(configuration.getDevice())) {
          // Wear device cannot toggle orientation.
          return;
        }
        State current = configuration.getDeviceState();
        State flip = configuration.getNextDeviceState(current);
        if (flip != null) {
          configuration.setDeviceState(flip);
        }
      });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
