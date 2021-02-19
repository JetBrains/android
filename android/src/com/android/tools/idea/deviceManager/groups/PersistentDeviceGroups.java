/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.groups;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@Service
public final class PersistentDeviceGroups {
  private final @NotNull List<@NotNull DeviceGroup> myDeviceGroups; // TODO: move this somewhere else and persist

  private PersistentDeviceGroups() {
    myDeviceGroups = new ArrayList<>();
  }

  public static @NotNull PersistentDeviceGroups getInstance() {
    return ApplicationManager.getApplication().getService(PersistentDeviceGroups.class);
  }

  @NotNull List<@NotNull DeviceGroup> getDeviceGroups() {
    return myDeviceGroups;
  }

  public void createDeviceGroup(@NotNull String name, @NotNull String description, @NotNull List<@NotNull GroupableDevice> devices) {
    DeviceGroup group = new DeviceGroup(name, description);
    group.getDevices().addAll(devices);
    myDeviceGroups.add(group);
  }
}
