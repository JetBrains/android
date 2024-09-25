/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.functional;

import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.intellij.util.Function;
import java.util.EnumSet;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/** Compat class for {@link AndroidDevice} for changed canRun signature. #api211 */
public abstract class AndroidDeviceCompat implements AndroidDevice {

  @Override
  public LaunchCompatibility canRun(
      com.android.sdklib.AndroidVersion androidVersion,
      IAndroidTarget iAndroidTarget,
      AndroidFacet facet,
      Function<AndroidFacet, EnumSet<HardwareFeature>> getRequiredHardwareFeatures,
      @Nullable Set<Abi> set) {
    return null;
  }
}
