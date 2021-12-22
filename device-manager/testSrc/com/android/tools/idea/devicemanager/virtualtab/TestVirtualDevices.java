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

import com.android.sdklib.internal.avd.AvdInfo;
import org.jetbrains.annotations.NotNull;

final class TestVirtualDevices {
  private TestVirtualDevices() {
  }

  static @NotNull VirtualDevice pixel5Api31(@NotNull AvdInfo avd) {
    return new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName("Pixel_5_API_31"))
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setApi("31")
      .setAvdInfo(avd)
      .build();
  }
}
