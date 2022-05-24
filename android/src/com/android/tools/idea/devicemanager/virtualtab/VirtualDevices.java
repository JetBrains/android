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
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.util.Targets;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

final class VirtualDevices {
  private VirtualDevices() {
  }

  static @NotNull Device build(@NotNull AvdInfo device) {
    return build(device, AvdManagerConnection.getDefaultAvdManagerConnection()::isAvdRunning);
  }

  @VisibleForTesting
  static @NotNull Device build(@NotNull AvdInfo device, @NotNull Predicate<@NotNull AvdInfo> isAvdRunning) {
    IdDisplay tag = device.getTag();
    AndroidVersion version = device.getAndroidVersion();

    return new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName(device.getName()))
      .setCpuArchitecture(device.getCpuArch())
      .setType(getType(tag))
      .setName(device.getDisplayName())
      .setOnline(isAvdRunning.test(device))
      .setTarget(Targets.toString(version, tag))
      .setApi(Integer.toString(version.getApiLevel()))
      .build();
  }

  private static @NotNull DeviceType getType(@NotNull IdDisplay tag) {
    if (tag.equals(SystemImage.WEAR_TAG)) {
      return DeviceType.WEAR_OS;
    }
    else if (tag.equals(SystemImage.ANDROID_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.GOOGLE_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_PLAY_STORE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else {
      return DeviceType.PHONE;
    }
  }
}
