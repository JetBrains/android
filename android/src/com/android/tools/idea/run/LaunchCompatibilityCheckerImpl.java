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

import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.sdk.AndroidPlatform;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchCompatibilityCheckerImpl implements LaunchCompatibilityChecker {
  @NotNull @VisibleForTesting final AndroidVersion myMinSdkVersion;
  @NotNull private final IAndroidTarget myProjectTarget;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final Set<Abi> mySupportedAbis;

  private LaunchCompatibilityCheckerImpl(@NotNull AndroidVersion minSdkVersion,
                                        @NotNull IAndroidTarget target,
                                        @NotNull AndroidFacet facet,
                                        @NotNull Set<Abi> supportedAbis) {
    myMinSdkVersion = minSdkVersion;
    myProjectTarget = target;
    myFacet = facet;
    mySupportedAbis = supportedAbis;
  }

  /**
   * Validates the given {@link AndroidDevice} and returns the {@link LaunchCompatibility}. This method
   * can block while obtaining the hardware features from the {@link AndroidFacet}.
   */
  @Slow
  @NotNull
  @Override
  public LaunchCompatibility validate(@NotNull AndroidDevice device) {
    LaunchCompatibility launchCompatibility = LaunchCompatibility.YES;

    return launchCompatibility.combine(device.canRun(myMinSdkVersion, myProjectTarget,
                                                     new RequiredHardwareFeatures(myFacet), mySupportedAbis));
  }

  /**
   * A supplier implemented as a static class to make its dependencies (a facet) explicit.
   */
  private static class RequiredHardwareFeatures implements Supplier<EnumSet<IDevice.HardwareFeature>> {
    @NotNull private final AndroidFacet myFacet;

    private RequiredHardwareFeatures(@NotNull AndroidFacet facet) { myFacet = facet; }

    @Override
    public EnumSet<IDevice.HardwareFeature> get() {
      return getRequiredHardwareFeatures(myFacet);
    }
  }

  /**
   * Returns the required hardware features from a given {@link AndroidFacet}.
   */
  @NotNull
  static EnumSet<IDevice.HardwareFeature> getRequiredHardwareFeatures(@NotNull AndroidFacet facet) {
    // Currently, we only look at whether the device supports the watch feature.
    // We may not want to search the device for every possible feature, but only a small subset of important
    // features, starting with hardware type watch.
    if (LaunchUtils.isWatchFeatureRequired(facet)) {
      return EnumSet.of(IDevice.HardwareFeature.WATCH);
    }
    else {
      return EnumSet.noneOf(IDevice.HardwareFeature.class);
    }
  }

  public static @Nullable LaunchCompatibilityChecker create(@NotNull AndroidFacet facet) {
    AndroidPlatform platform = AndroidPlatforms.getInstance(facet.getModule());
    if (platform == null) {
      return null;
    }

    AndroidModel androidModel = AndroidModel.get(facet);
    if (androidModel == null) {
      return null;
    }

    AndroidVersion minSdkVersion = getMinSdkVersion(facet);
    Set<Abi> supportedAbis = androidModel.getSupportedAbis();

    return new LaunchCompatibilityCheckerImpl(minSdkVersion, platform.getTarget(), facet, supportedAbis);
  }

  private static AndroidVersion getMinSdkVersion(@NotNull AndroidFacet facet) {
    ListenableFuture<AndroidVersion> minSdkVersionFuture = StudioAndroidModuleInfo.getInstance(facet).getRuntimeMinSdkVersion();
    if (minSdkVersionFuture.isDone()) {
      try {
        return minSdkVersionFuture.get();
      }
      catch (ExecutionException | InterruptedException ignored) {
        // It'd be nice to log something here, but we're constantly validating
        // launch compatibility so that would result in a lot of spam.
      }
    }
    return AndroidVersion.DEFAULT;
  }
}
