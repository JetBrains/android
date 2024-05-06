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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import icons.StudioIcons;
import java.time.Instant;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceComparatorTest {
  @Test
  public void compareConnectionTime() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setConnectionTime(Instant.parse("2019-06-03T20:57:00.687Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_28"))
      .setConnectionTime(Instant.parse("2019-06-03T20:56:58.176Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareConnectionTimeNullsLast() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareValid() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "reason"))
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareType() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("LGE Nexus 5X")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareName() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void testValidityPrecedesConnectionTime() {
    var device1 = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_3_API_28"))
      .setConnectionTime(Instant.parse("2019-06-03T20:56:58.176Z"))
      .setName("Pixel 3 API 28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    var device2 = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_28"))
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "reason"))
      .setConnectionTime(Instant.parse("2019-06-03T20:57:00.687Z"))
      .setName("Pixel 2 XL API 28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }
}
