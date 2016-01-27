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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class LaunchCompatibilityChecker {
  private final AndroidVersion myMinSdkVersion;
  private final IAndroidTarget myProjectTarget;
  private final EnumSet<IDevice.HardwareFeature> myRequiredHardwareFeatures;

  public LaunchCompatibilityChecker(@NotNull AndroidVersion minSdkVersion,
                                    @NotNull IAndroidTarget target,
                                    @NotNull EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures) {
    myMinSdkVersion = minSdkVersion;
    myProjectTarget = target;
    myRequiredHardwareFeatures = requiredHardwareFeatures;
  }

  public LaunchCompatibility validate(@NotNull AndroidDevice device) {
    return device.canRun(myMinSdkVersion, myProjectTarget, myRequiredHardwareFeatures);
  }
}
