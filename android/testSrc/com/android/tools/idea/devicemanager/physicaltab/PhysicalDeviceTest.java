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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTest {
  @Test
  public void sort() {
    Instant time1 = Instant.parse("2021-03-24T22:38:05.890571Z");
    Instant time2 = Instant.parse("2021-03-24T22:38:05.890570Z");

    Object device1 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("serialNumber1"))
      .setLastOnlineTime(time1)
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    Object device2 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("serialNumber2"))
      .setLastOnlineTime(time2)
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .addConnectionType(ConnectionType.USB)
      .build();

    Object device3 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("serialNumber3"))
      .setLastOnlineTime(time1)
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    Object device4 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("serialNumber4"))
      .setLastOnlineTime(time2)
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    Object device5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("serialNumber5"))
      .setName("Google Pixel 3")
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    List<Object> devices = Arrays.asList(device5, device4, device2, device1, device3);

    // Act
    devices.sort(null);

    // Assert
    assertEquals(Arrays.asList(device1, device2, device3, device4, device5), devices);
  }
}
