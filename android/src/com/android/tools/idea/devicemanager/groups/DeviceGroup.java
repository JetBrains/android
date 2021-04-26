/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.groups;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

final class DeviceGroup {
  private final @NotNull String myName;
  private final @NotNull String myDescription;
  private final @NotNull List<@NotNull GroupableDevice> myDevices;

  DeviceGroup(@NotNull String name, @NotNull String description) {
    myName = name;
    myDescription = description;
    myDevices = new ArrayList<>();
  }

  @NotNull String getName() {
    return myName;
  }

  @NotNull String getDescription() {
    return myDescription;
  }

  @NotNull List<@NotNull GroupableDevice> getDevices() {
    return myDevices;
  }
}
