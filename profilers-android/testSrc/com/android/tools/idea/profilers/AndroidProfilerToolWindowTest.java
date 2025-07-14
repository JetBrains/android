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
package com.android.tools.idea.profilers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import org.junit.Test;

public class AndroidProfilerToolWindowTest {

  @Test
  public void testDeviceDisplayName() {
    IDevice device = mock(IDevice.class);
    when(device.getSerialNumber()).thenReturn("Serial");
    when(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Manufacturer");
    when(device.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model");
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(device)).isEqualTo("Manufacturer Model");

    IDevice deviceWithEmptyManufacturer = mock(IDevice.class);
    when(deviceWithEmptyManufacturer.getSerialNumber()).thenReturn("Serial");
    when(deviceWithEmptyManufacturer.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model");
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(deviceWithEmptyManufacturer)).isEqualTo("Model");


    IDevice deviceWithSerialInModel = mock(IDevice.class);
    when(deviceWithSerialInModel.getSerialNumber()).thenReturn("Serial");
    when(deviceWithSerialInModel.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Manufacturer");
    when(deviceWithSerialInModel.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model-Serial");
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(deviceWithSerialInModel)).isEqualTo("Manufacturer Model");
  }
}