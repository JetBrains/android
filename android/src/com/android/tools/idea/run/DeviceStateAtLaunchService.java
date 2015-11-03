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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DeviceStateAtLaunchService {
  /** A map from launch configuration id to the state of devices at the time of the launch. */
  private TIntObjectHashMap<DeviceStateAtLaunch> myLastUsedDevices = new TIntObjectHashMap<DeviceStateAtLaunch>();

  public static DeviceStateAtLaunchService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DeviceStateAtLaunchService.class);
  }

  public void setDevicesUsedInLaunch(int id, @NotNull Set<IDevice> usedDevices, @NotNull Set<IDevice> availableDevices) {
    myLastUsedDevices.put(id, new DeviceStateAtLaunch(usedDevices, availableDevices));
  }

  @Nullable
  public DeviceStateAtLaunch getDevicesUsedInLastLaunch(int id) {
    return myLastUsedDevices.get(id);
  }
}
