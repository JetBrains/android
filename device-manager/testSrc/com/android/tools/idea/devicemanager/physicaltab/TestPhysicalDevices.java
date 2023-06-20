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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.ConnectionType;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.SerialNumber;
import org.jetbrains.annotations.NotNull;

public final class TestPhysicalDevices {
  public static final @NotNull PhysicalDevice ONLINE_COMPAL_FALSTER = new PhysicalDevice.Builder()
    .setKey(Ipv4Address.parse("192.168.1.123:5555").orElseThrow())
    .setType(DeviceType.WEAR_OS)
    .setName("Compal Falster")
    .setTarget("Android 11.0")
    .setAndroidVersion(new AndroidVersion(30))
    .addConnectionType(ConnectionType.WI_FI)
    .build();

  static final Key GOOGLE_PIXEL_3_KEY = new SerialNumber("86UX00F4R");

  public static final @NotNull PhysicalDevice GOOGLE_PIXEL_3 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_3_KEY)
    .setName("Google Pixel 3")
    .setTarget("Android 12.0")
    .setAndroidVersion(new AndroidVersion(31))
    .build();

  public static final @NotNull PhysicalDevice ONLINE_GOOGLE_PIXEL_3 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_3_KEY)
    .setName("Google Pixel 3")
    .setTarget("Android 12.0")
    .setAndroidVersion(new AndroidVersion(31))
    .addConnectionType(ConnectionType.USB)
    .build();

  private static final Key GOOGLE_PIXEL_5_KEY = new SerialNumber("0A071FDD4003ZG");

  static final @NotNull PhysicalDevice GOOGLE_PIXEL_5 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_5_KEY)
    .setName("Google Pixel 5")
    .setTarget("Android 11.0")
    .setAndroidVersion(new AndroidVersion(30))
    .build();

  static final @NotNull PhysicalDevice ONLINE_GOOGLE_PIXEL_5 = new PhysicalDevice.Builder()
    .setKey(GOOGLE_PIXEL_5_KEY)
    .setName("Google Pixel 5")
    .setTarget("Android 11.0")
    .setAndroidVersion(new AndroidVersion(30))
    .addConnectionType(ConnectionType.USB)
    .build();

  /**
   * An ACID virtual device
   */
  static final @NotNull PhysicalDevice ONLINE_GOOGLE_PIXEL_ANDROID = new PhysicalDevice.Builder()
    .setKey(Localhost.parse("localhost:46043").orElseThrow())
    .setName("google Pixel (Android)")
    .setTarget("Android 10.0")
    .setAndroidVersion(new AndroidVersion(29))
    .addConnectionType(ConnectionType.UNKNOWN)
    .build();

  private TestPhysicalDevices() {
  }
}
