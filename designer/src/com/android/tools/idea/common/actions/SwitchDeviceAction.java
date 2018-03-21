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
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.List;

import static com.android.ide.common.rendering.HardwareConfigHelper.isNexus;

/**
 * Change {@link DesignSurface}'s configuration device
 */
public class SwitchDeviceAction extends AnAction {

  private final DesignSurface mySurface;

  public SwitchDeviceAction(DesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Configuration configuration = mySurface.getConfiguration();
    if (configuration == null) {
      return;
    }
    List<Device> devices = ConfigurationManager.getOrCreateInstance(configuration.getModule()).getDevices();
    List<Device> applicable = Lists.newArrayList();
    for (Device device : devices) {
      if (isNexus(device)) {
        applicable.add(device);
      }
    }
    Device currentDevice = configuration.getDevice();
    for (int i = 0, n = applicable.size(); i < n; i++) {
      if (applicable.get(i) == currentDevice) {
        Device newDevice = applicable.get((i + 1) % applicable.size());
        configuration.setDevice(newDevice, true);
        break;
      }
    }
  }
}
