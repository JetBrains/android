/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.adaptiveicon;

import static com.android.tools.idea.actions.DesignerDataKeys.CONFIGURATIONS;
import static com.android.tools.idea.uibuilder.model.NlModelHelperKt.CUSTOM_DENSITY_ID;

import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.configurations.Configuration;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

public class DensityMenuAction extends DropDownAction {
  private static final Density[] DENSITIES = new Density[] {Density.MEDIUM, Density.HIGH, Density.XHIGH, Density.XXHIGH, Density.XXXHIGH};

  public DensityMenuAction() {
    super("Device Screen Density", "Device Screen Density", null);
    for (Density density : DENSITIES) {
      add(new SetDensityAction(density));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
    if (configurations == null || configurations.isEmpty()) {
      return;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    int currentValue = configuration.getDensity().getDpiValue();
    for (int i = 0; i < DENSITIES.length; i++) {
      if (DENSITIES[i].getDpiValue() >= currentValue || i == DENSITIES.length - 1) {
        e.getPresentation().setText(DENSITIES[i].getResourceValue());
        break;
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  private static class SetDensityAction extends AnAction {
    private final Density myDensity;

    private SetDensityAction(@NotNull Density density) {
      super(density.getResourceValue());
      myDensity = density;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
      if (configurations == null || configurations.isEmpty()) {
        return;
      }
      configurations.forEach(config -> {
        Device original = config.getCachedDevice();
        if (original == null) {
          return;
        }
        Device.Builder deviceBuilder = new Device.Builder(original); // doesn't copy tag id
        deviceBuilder.setTagId(original.getTagId());
        deviceBuilder.setName("Custom");
        deviceBuilder.setId(CUSTOM_DENSITY_ID);
        Device device = deviceBuilder.build();
        device.getAllStates().forEach(state -> state.getHardware().getScreen().setPixelDensity(myDensity));

        config.setEffectiveDevice(device, device.getDefaultState());
      });
    }
  }
}
