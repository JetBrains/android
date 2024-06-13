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

import static org.junit.Assert.assertEquals;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager.DeviceFilter;
import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.android.tools.idea.avdmanager.ui.DeviceSupplier;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceSupplierTest {
  @NotNull
  private final DeviceManagerConnection myConnection = Mockito.mock(DeviceManagerConnection.class);

  @NotNull
  private final EnumSet<DeviceFilter> mySystemImageFilter = EnumSet.of(DeviceFilter.SYSTEM_IMAGES);

  private final DeviceSupplier mySupplier = new DeviceSupplier(() -> myConnection);

  @Test
  public void get() {
    // Arrange
    var deprecatedWearOsDevice = Mockito.mock(Device.class);
    Mockito.when(deprecatedWearOsDevice.getId()).thenReturn("wear_square");
    Mockito.when(deprecatedWearOsDevice.getTagId()).thenReturn("android-wear");

    var device = mockDevice("Nexus S", "Google");

    Mockito.when(myConnection.getDevices(mySystemImageFilter)).thenReturn(List.of(deprecatedWearOsDevice));
    Mockito.when(myConnection.getDevices(EnumSet.complementOf(mySystemImageFilter))).thenReturn(List.of(device));

    // Act
    var devices = mySupplier.get();

    // Assert
    assertEquals(List.of(device), List.copyOf(devices));
  }

  @Test
  public void getSystemImageCollectionAndComplementContainDevicesWithEqualKeys() {
    // Arrange
    var wearOsSquare1 = mockDevice("wearos_square", "Google");
    var wearOsSquare2 = mockDevice("wearos_square", "Google");

    Mockito.when(myConnection.getDevices(mySystemImageFilter)).thenReturn(List.of(wearOsSquare1));
    Mockito.when(myConnection.getDevices(EnumSet.complementOf(mySystemImageFilter))).thenReturn(List.of(wearOsSquare2));

    // Act
    var devices = mySupplier.get();

    // Assert
    assertEquals(List.of(wearOsSquare1), List.copyOf(devices));
  }

  @NotNull
  private static Device mockDevice(@NotNull String id, @NotNull @SuppressWarnings("SameParameterValue") String manufacturer) {
    var device = Mockito.mock(Device.class);
    Mockito.when(device.getId()).thenReturn(id);
    Mockito.when(device.getManufacturer()).thenReturn(manufacturer);

    return device;
  }
}
