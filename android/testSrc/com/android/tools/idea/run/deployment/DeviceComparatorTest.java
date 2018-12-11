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

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.intellij.util.ThreeState;
import java.time.Clock;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceComparatorTest {
  private ConnectionTimeService myService;

  @Before
  public void newService() {
    Clock clock = Mockito.mock(Clock.class);
    Mockito.when(clock.instant()).thenReturn(Instant.parse("2018-11-28T01:15:27.000Z"));

    myService = new ConnectionTimeService(clock);
  }

  @Test
  public void compareConnected() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey("Pixel_3_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setConnected(true)
      .build(null, myService);

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build(null, myService);

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareValid() {
    LaunchCompatibilityChecker checker = Mockito.mock(LaunchCompatibilityChecker.class);

    AndroidDevice androidDevice1 = Mockito.mock(AndroidDevice.class);
    Mockito.when(checker.validate(androidDevice1)).thenReturn(LaunchCompatibility.YES);

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey("Pixel_3_API_28")
      .setAndroidDevice(androidDevice1)
      .build(checker, myService);

    AndroidDevice androidDevice2 = Mockito.mock(AndroidDevice.class);
    Mockito.when(checker.validate(androidDevice2)).thenReturn(new LaunchCompatibility(ThreeState.NO, null));

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(androidDevice2)
      .build(checker, myService);

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareType() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey("Pixel_3_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setConnected(true)
      .build(null, myService);

    Device device2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey("00fff9d2279fa601")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build(null, myService);

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }

  @Test
  public void compareName() {
    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API 28")
      .setKey("Pixel_2_XL_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setConnected(true)
      .build(null, myService);

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 28")
      .setKey("Pixel_3_API_28")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build(null, myService);

    assertTrue(new DeviceComparator().compare(device1, device2) < 0);
  }
}
