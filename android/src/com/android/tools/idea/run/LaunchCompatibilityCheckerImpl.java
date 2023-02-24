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
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.run.util.SwapInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchCompatibilityCheckerImpl implements LaunchCompatibilityChecker {
  @NotNull @VisibleForTesting final AndroidVersion myMinSdkVersion;
  @NotNull private final IAndroidTarget myProjectTarget;
  @NotNull private final AndroidFacet myFacet;
  @Nullable private final ExecutionEnvironment myEnvironment;
  @Nullable private final AndroidRunConfigurationBase myAndroidRunConfigurationBase;
  @NotNull private final Set<Abi> mySupportedAbis;

  public LaunchCompatibilityCheckerImpl(@NotNull AndroidVersion minSdkVersion,
                                        @NotNull IAndroidTarget target,
                                        @NotNull AndroidFacet facet,
                                        @Nullable ExecutionEnvironment environment,
                                        @Nullable AndroidRunConfigurationBase androidRunConfigurationBase,
                                        @NotNull Set<Abi> supportedAbis) {
    assert (environment == null && androidRunConfigurationBase == null) || (environment != null && androidRunConfigurationBase != null);
    myMinSdkVersion = minSdkVersion;
    myProjectTarget = target;
    myEnvironment = environment;
    myAndroidRunConfigurationBase = androidRunConfigurationBase;
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

    if (myEnvironment != null && myAndroidRunConfigurationBase != null) {
      SwapInfo swapInfo = myEnvironment.getUserData(SwapInfo.SWAP_INFO_KEY);
      if (swapInfo != null) {
        if (device.getVersion().compareTo(AndroidVersion.VersionCodes.O, null) < 0) {
          launchCompatibility =
            new LaunchCompatibility(State.WARNING, "The device needs to be running Oreo or newer.");
        }
        else if (!device.isRunning()) {
          launchCompatibility =
            new LaunchCompatibility(State.ERROR, "Please ensure the target device/emulator is running.");
        }
        else {
          try {
            ApplicationIdProvider applicationIdProvider = myAndroidRunConfigurationBase.getApplicationIdProvider();
            if (applicationIdProvider == null) {
              return new LaunchCompatibility(State.ERROR, "Cannot get applicationId.");
            }
            Client client = device.getLaunchedDevice().get().getClient(applicationIdProvider.getPackageName());
            if (client == null) {
              launchCompatibility = new LaunchCompatibility(
                State.ERROR,
                "App not running on device. Please first install/run the app on the target device/emulator.");
            }
          }
          catch (InterruptedException | ExecutionException | ApkProvisionException e) {
            launchCompatibility =
              new LaunchCompatibility(State.WARNING, "Could not determine if device is compatible.");
          }
        }
      }
    }

    return launchCompatibility.combine(device.canRun(myMinSdkVersion, myProjectTarget, myFacet,
                                                     LaunchCompatibilityCheckerImpl::getRequiredHardwareFeatures, mySupportedAbis));
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

  public static @Nullable LaunchCompatibilityChecker create(@NotNull AndroidFacet facet,
                                                            @Nullable ExecutionEnvironment env,
                                                            @Nullable AndroidRunConfigurationBase androidRunConfigurationBase) {
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

    return new LaunchCompatibilityCheckerImpl(minSdkVersion, platform.getTarget(), facet, env, androidRunConfigurationBase, supportedAbis);
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
