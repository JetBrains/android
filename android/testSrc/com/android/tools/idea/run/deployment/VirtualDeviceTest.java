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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class VirtualDeviceTest {
  private IDevice myConnectedDevice;
  private Collection<IDevice> myConnectedDevices;

  @Before
  public void newConnectedDevices() {
    myConnectedDevice = Mockito.mock(IDevice.class);
    Mockito.when(myConnectedDevice.getAvdName()).thenReturn("Pixel_2_XL_API_27");

    myConnectedDevices = new ArrayList<>();
    myConnectedDevices.add(myConnectedDevice);
  }

  @Test
  public void newVirtualDeviceIsConnected() {
    AvdInfo virtualDevice = new AvdInfo(
      "Pixel_2_XL_API_27",
      new File("/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_27.ini"),
      "/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_27.avd",
      Mockito.mock(ISystemImage.class),
      null);

    assertEquals(new VirtualDevice(true, "Pixel 2 XL API 27"), VirtualDevice.newVirtualDevice(virtualDevice, myConnectedDevices));
    assertEquals(Collections.emptyList(), myConnectedDevices);
  }

  @Test
  public void newVirtualDeviceIsntConnected() {
    AvdInfo virtualDevice = new AvdInfo(
      "Pixel_2_XL_API_28",
      new File("/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_28.ini"),
      "/usr/local/google/home/juancnuno/.android/avd/Pixel_2_XL_API_28.avd",
      Mockito.mock(ISystemImage.class),
      null);

    assertEquals(new VirtualDevice(false, "Pixel 2 XL API 28"), VirtualDevice.newVirtualDevice(virtualDevice, myConnectedDevices));
    assertEquals(Collections.singletonList(myConnectedDevice), myConnectedDevices);
  }
}
