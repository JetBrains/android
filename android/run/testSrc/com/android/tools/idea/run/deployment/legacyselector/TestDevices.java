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

import com.android.tools.idea.run.AndroidDevice;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

final class TestDevices {
  private TestDevices() {
  }

  @NotNull
  static VirtualDevice buildPixel3Api30() {
    return new VirtualDevice.Builder()
      .setKey(Keys.PIXEL_3_API_30)
      .setName("Pixel 3 API 30")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();
  }

  @NotNull
  static VirtualDevice buildPixel4Api30() {
    return new VirtualDevice.Builder()
      .setKey(Keys.PIXEL_4_API_30)
      .setName("Pixel 4 API 30")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();
  }
}
