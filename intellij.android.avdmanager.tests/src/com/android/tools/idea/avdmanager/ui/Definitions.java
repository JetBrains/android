/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

final class Definitions {
  private Definitions() {
  }

  @NotNull
  static Device mockPhone() {
    return mockDefinition(null, mockHardware(0), "Pixel Fold", "pixel_fold");
  }

  @NotNull
  static Device mockTablet() {
    return mockDefinition(null, mockHardware(10.055), "Pixel Tablet", "pixel_tablet");
  }

  @NotNull
  static Device mockWearOsDefinition() {
    return mockDefinition("android-wear", mockHardware(0), "Wear OS Square", "wearos_square");
  }

  @NotNull
  static Device mockDesktop() {
    return mockDefinition("android-desktop", null, "Medium Desktop", "desktop_medium");
  }

  @NotNull
  static Device mockTv() {
    return mockDefinition("android-tv", mockHardware(0), "Television (1080p)", "tv_1080p");
  }

  @NotNull
  static Device mockAutomotiveDefinition() {
    return mockDefinition("android-automotive", mockHardware(0), "Automotive (1024p landscape)", "automotive_1024p_landscape");
  }

  @NotNull
  static Device mockLegacyDefinition() {
    var definition = mockDefinition(null, null, "Nexus S", "Nexus S");
    Mockito.when(definition.getIsDeprecated()).thenReturn(true);

    return definition;
  }

  @NotNull
  static Device mockDefinition(@Nullable String tagId, @Nullable Hardware hardware, @NotNull String name, @NotNull String id) {
    var definition = Mockito.mock(Device.class);

    Mockito.when(definition.getTagId()).thenReturn(tagId);
    Mockito.when(definition.getDefaultHardware()).thenReturn(hardware);
    Mockito.when(definition.getDisplayName()).thenReturn(name);
    Mockito.when(definition.getId()).thenReturn(id);

    return definition;
  }

  @NotNull
  static Hardware mockHardware(double length) {
    var screen = Mockito.mock(Screen.class);
    Mockito.when(screen.getDiagonalLength()).thenReturn(length);

    var hardware = Mockito.mock(Hardware.class);
    Mockito.when(hardware.getScreen()).thenReturn(screen);

    return hardware;
  }
}
