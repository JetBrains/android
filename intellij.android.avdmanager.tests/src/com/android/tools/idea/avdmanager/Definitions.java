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
package com.android.tools.idea.avdmanager;

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
    return mockDefinition(null, mockHardware(0));
  }

  @NotNull
  static Device mockTablet() {
    return mockDefinition(null, mockHardware(10.055));
  }

  @NotNull
  static Device mockWearOsDefinition() {
    return mockDefinition("android-wear", mockHardware(0));
  }

  @NotNull
  static Device mockDesktop() {
    return mockDefinition("android-desktop", null);
  }

  @NotNull
  static Device mockTv() {
    return mockDefinition("android-tv", mockHardware(0));
  }

  @NotNull
  static Device mockAutomotiveDefinition() {
    return mockDefinition("android-automotive", mockHardware(0));
  }

  @NotNull
  static Device mockLegacyDefinition() {
    var definition = mockDefinition(null, null);
    Mockito.when(definition.getIsDeprecated()).thenReturn(true);

    return definition;
  }

  @NotNull
  private static Device mockDefinition(@Nullable String id, @Nullable Hardware hardware) {
    var definition = Mockito.mock(Device.class);

    Mockito.when(definition.getTagId()).thenReturn(id);
    Mockito.when(definition.getDefaultHardware()).thenReturn(hardware);

    return definition;
  }

  @NotNull
  private static Hardware mockHardware(double length) {
    var screen = Mockito.mock(Screen.class);
    Mockito.when(screen.getDiagonalLength()).thenReturn(length);

    var hardware = Mockito.mock(Hardware.class);
    Mockito.when(hardware.getScreen()).thenReturn(screen);

    return hardware;
  }
}
