/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

public class ConnectedAndroidDeviceTest {
  @Test
  public void getName_emulatorWithNoAvdName() {
    IDevice emulatorWithNoAvdName = createMockRunningEmulator(null);
    assertThat(new ConnectedAndroidDevice(emulatorWithNoAvdName, null).getName()).isEqualTo("Google Pixel [local:5554]");
  }

  @Test
  public void getName_emulatorWithAvdName() {
    IDevice emulatorWithNoAvdName = createMockRunningEmulator("My Pixel");
    assertThat(new ConnectedAndroidDevice(emulatorWithNoAvdName, null).getName()).isEqualTo("Google Pixel [My Pixel]");
  }

  private static IDevice createMockRunningEmulator(@Nullable String avdName) {
    IDevice device = mock(IDevice.class);
    when(device.isEmulator()).thenReturn(true);
    when(device.getAvdName()).thenReturn(avdName);
    when(device.getSerialNumber()).thenReturn("local:5554");
    when(device.getProperty(eq(IDevice.PROP_DEVICE_MANUFACTURER))).thenReturn("Google");
    when(device.getProperty(eq(IDevice.PROP_DEVICE_MODEL))).thenReturn("Pixel");
    when(device.getVersion()).thenReturn(new AndroidVersion(28, null));
    return device;
  }
}