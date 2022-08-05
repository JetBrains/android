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
package com.android.tools.idea.devicemanager;

import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public enum DeviceType {
  PHONE(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE),
  TV(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV),
  WEAR_OS(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR),
  AUTOMOTIVE(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR, StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR);

  private final @NotNull Icon myVirtualIcon;
  private final @NotNull Icon myPhysicalIcon;

  DeviceType(@NotNull Icon virtualIcon, @NotNull Icon physicalIcon) {
    myVirtualIcon = virtualIcon;
    myPhysicalIcon = physicalIcon;
  }

  public final @NotNull Icon getVirtualIcon() {
    return myVirtualIcon;
  }

  public final @NotNull Icon getPhysicalIcon() {
    return myPhysicalIcon;
  }
}
