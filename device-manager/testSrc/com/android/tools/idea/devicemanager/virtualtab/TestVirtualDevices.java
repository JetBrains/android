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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Key;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

public final class TestVirtualDevices {
  public static final @NotNull Key PIXEL_5_API_31_KEY = newKey("Pixel_5_API_31");

  private TestVirtualDevices() {
  }

  public static @NotNull VirtualDevice pixel5Api31(@NotNull AvdInfo avd) {
    return new VirtualDevice.Builder()
      .setKey(PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setAvdInfo(avd)
      .build();
  }

  static @NotNull VirtualDevice onlinePixel5Api31(@NotNull AvdInfo avd) {
    return new VirtualDevice.Builder()
      .setKey(PIXEL_5_API_31_KEY)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setState(VirtualDevice.State.LAUNCHED)
      .setAvdInfo(avd)
      .build();
  }

  /**
   * @param name what is returned by com.android.sdklib.internal.avd.AvdInfo::getName
   */
  public static @NotNull Key newKey(@NotNull String name) {
    return new VirtualDevicePath(Paths.get(System.getProperty("user.home"), ".android", "avd", name + ".avd").toString());
  }
}
