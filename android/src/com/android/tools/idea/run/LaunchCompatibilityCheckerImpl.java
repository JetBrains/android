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

import static com.intellij.util.ThreeState.NO;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.run.util.LaunchUtils;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchCompatibilityCheckerImpl implements LaunchCompatibilityChecker {
  @NotNull private final AndroidVersion myMinSdkVersion;
  @NotNull private final IAndroidTarget myProjectTarget;
  @NotNull private final EnumSet<IDevice.HardwareFeature> myRequiredHardwareFeatures;
  @NotNull private final AndroidFacet myFacet;
  @Nullable private final ExecutionEnvironment myEnvironment;
  @Nullable private final AndroidRunConfigurationBase myAndroidRunConfigurationBase;
  @Nullable private final Set<String> mySupportedAbis;

  public LaunchCompatibilityCheckerImpl(@NotNull AndroidVersion minSdkVersion,
                                        @NotNull IAndroidTarget target,
                                        @NotNull EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures,
                                        @NotNull AndroidFacet facet,
                                        @Nullable ExecutionEnvironment environment,
                                        @Nullable AndroidRunConfigurationBase androidRunConfigurationBase,
                                        @Nullable Set<String> supportedAbis) {
    assert (environment == null && androidRunConfigurationBase == null) || (environment != null && androidRunConfigurationBase != null);
    myMinSdkVersion = minSdkVersion;
    myProjectTarget = target;
    myRequiredHardwareFeatures = requiredHardwareFeatures;
    myEnvironment = environment;
    myAndroidRunConfigurationBase = androidRunConfigurationBase;
    myFacet = facet;
    mySupportedAbis = supportedAbis;
  }

  @NotNull
  @Override
  public LaunchCompatibility validate(@NotNull AndroidDevice device) {
    LaunchCompatibility launchCompatibility = LaunchCompatibility.YES;

    if (myEnvironment != null && myAndroidRunConfigurationBase != null) {
      Boolean applyChanges = myEnvironment.getCopyableUserData(ApplyChangesAction.KEY);
      Boolean codeSwap = myEnvironment.getCopyableUserData(CodeSwapAction.KEY);
      if ((applyChanges != null && applyChanges) || (codeSwap != null && codeSwap)) {
        if (device.getVersion().compareTo(AndroidVersion.VersionCodes.O, null) < 0) {
          launchCompatibility = new LaunchCompatibility(NO, "The device needs to be running Oreo or newer.");
        }
        else if (!device.isRunning()) {
          launchCompatibility = new LaunchCompatibility(NO, "Please ensure the target device/emulator is running.");
        }
        else {
          try {
            ApplicationIdProvider applicationIdProvider = myAndroidRunConfigurationBase.getApplicationIdProvider(myFacet);
            Client client = device.getLaunchedDevice().get().getClient(applicationIdProvider.getPackageName());
            if (client == null) {
              launchCompatibility = new LaunchCompatibility(
                NO, "App not running on device. Please first install/run the app on the target device/emulator.");
            }
          }
          catch (InterruptedException | ExecutionException | ApkProvisionException e) {
            launchCompatibility = new LaunchCompatibility(NO, "Could not determine if device is compatible.");
          }
        }
      }
    }

    return launchCompatibility.combine(device.canRun(myMinSdkVersion, myProjectTarget, myRequiredHardwareFeatures, mySupportedAbis));
  }

  public static LaunchCompatibilityChecker create(@NotNull AndroidFacet facet,
                                                  @Nullable ExecutionEnvironment env,
                                                  @Nullable AndroidRunConfigurationBase androidRunConfigurationBase) {
    AndroidVersion minSdkVersion = AndroidModuleInfo.getInstance(facet).getRuntimeMinSdkVersion();

    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      throw new IllegalStateException("Android platform not set for module: " + facet.getModule().getName());
    }

    // Currently, we only look at whether the device supports the watch feature.
    // We may not want to search the device for every possible feature, but only a small subset of important
    // features, starting with hardware type watch.
    EnumSet<IDevice.HardwareFeature> requiredHardwareFeatures;
    if (LaunchUtils.isWatchFeatureRequired(facet)) {
      requiredHardwareFeatures = EnumSet.of(IDevice.HardwareFeature.WATCH);
    }
    else {
      requiredHardwareFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    }

    Set<String> supportedAbis = facet.getConfiguration().getModel() instanceof AndroidModuleModel ?
                                ((AndroidModuleModel)facet.getConfiguration().getModel()).getSelectedVariant().getMainArtifact()
                                  .getAbiFilters() :
                                null;

    return new LaunchCompatibilityCheckerImpl(
      minSdkVersion, platform.getTarget(), requiredHardwareFeatures, facet, env, androidRunConfigurationBase, supportedAbis);
  }
}
