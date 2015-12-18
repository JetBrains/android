/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.sdklib.AndroidVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class AndroidDeviceComparator implements Comparator<AndroidDevice> {
  @Override
  public int compare(@NotNull AndroidDevice device1, @NotNull AndroidDevice device2) {
    // prefer devices that are running
    if (device1.isRunning() != device2.isRunning()) {
      return device1.isRunning() ? -1 : 1;
    }

    // prefer devices with higher API level
    AndroidVersion version1 = device1.getVersion();
    AndroidVersion version2 = device2.getVersion();
    if (!version1.equals(version2)) {
      return version2.compareTo(version1);
    }

    // prefer real devices to AVDs
    if (device1.isVirtual() != device2.isVirtual()) {
      return device1.isVirtual() ? 1 : -1;
    }

    // Provide a consistent ordering: use serial numbers to compare devices that are identical in other respects
    return device1.getSerial().compareTo(device2.getSerial());
  }
}
