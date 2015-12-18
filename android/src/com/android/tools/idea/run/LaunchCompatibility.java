/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.google.common.base.Objects;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public class LaunchCompatibility {
  @NonNls private static final String GOOGLE_APIS_TARGET_NAME = "Google APIs";

  private final ThreeState myCompatible;
  private final String myReason;

  @VisibleForTesting
  public LaunchCompatibility(ThreeState compatible, @Nullable String reason) {
    myCompatible = compatible;
    myReason = reason;
  }

  public ThreeState isCompatible() {
    return myCompatible;
  }

  @Nullable
  public String getReason() {
    return myReason;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("compatible", myCompatible).add("reason", myReason).toString();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LaunchCompatibility &&
           myCompatible == ((LaunchCompatibility)o).myCompatible &&
           Objects.equal(myReason, ((LaunchCompatibility)o).myReason);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myCompatible, myReason);
  }

  private static final LaunchCompatibility YES = new LaunchCompatibility(ThreeState.YES, null);

  /**
   * Returns whether an application with the given requirements can be run on the given device.
   *
   * @param minSdkVersion    minSdkVersion specified by the application
   * @param projectTarget    android target corresponding to the targetSdkVersion
   * @param requiredFeatures required list of hardware features
   * @param device           the device to check compatibility against
   * @param avdTarget        the target platform corresponding to the AVD, if the device happens to be an emulator
   * @return a {@link ThreeState} indicating whether the application can be run on the device, and a reason if it isn't
   * compatible.
   */
  @NotNull
  public static LaunchCompatibility canRunOnDevice(@NotNull AndroidVersion minSdkVersion,
                                                   @NotNull IAndroidTarget projectTarget,
                                                   @NotNull EnumSet<IDevice.HardwareFeature> requiredFeatures,
                                                   @NotNull AndroidDevice device,
                                                   @Nullable IAndroidTarget avdTarget) {
    // check if the device has the required minApi
    // note that in cases where targetSdk is a preview platform, gradle sets minsdk to be the same as targetsdk,
    // so as to only allow running on those systems
    AndroidVersion deviceVersion = device.getVersion();
    if (!deviceVersion.canRun(minSdkVersion)) {
      String reason = String.format("minSdk(%1$s) %3$s deviceSdk(%2$s)",
                                    minSdkVersion,
                                    deviceVersion,
                                    minSdkVersion.getCodename() == null ? ">" : "!=");
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    // check if the device provides the required features
    for (IDevice.HardwareFeature feature : requiredFeatures) {
      if (!device.supportsFeature(feature)) {
        return new LaunchCompatibility(ThreeState.NO, "missing feature: " + feature);
      }
    }

    // Typically, we only need to check that features required by the apk are supported by the device, which is done above
    // In the case of watch though, we do an explicit check in the other direction: if the device is a watch, we don't want
    // non-watch apks to be installed on it.
    if (device.supportsFeature(IDevice.HardwareFeature.WATCH)) {
      if (!requiredFeatures.contains(IDevice.HardwareFeature.WATCH)) {
        return new LaunchCompatibility(ThreeState.NO, "missing uses-feature watch, non-watch apks cannot be launched on a watch");
      }
    }

    // we are done with checks for platform targets
    if (projectTarget.isPlatform()) {
      return YES;
    }

    // Add-ons specify a list of libraries. We need to check that the required libraries are available on the device.
    // See AddOnTarget#canRunOn
    List<IAndroidTarget.OptionalLibrary> additionaLibs = projectTarget.getAdditionalLibraries();
    if (additionaLibs.isEmpty()) {
      return YES;
    }

    if (avdTarget == null) {
      String targetName = projectTarget.getName();
      if (GOOGLE_APIS_TARGET_NAME.equals(targetName)) {
        // We'll assume that Google APIs are available on all devices.
        return YES;
      } else {
        // Unsure because we don't have an easy way of determining whether those libraries are on a device
        return new LaunchCompatibility(ThreeState.UNSURE, "unsure if device supports addon: " + targetName);
      }
    } else {
      // Note: A better way to do this is to actually look at the manifest for all of its requirements, and then look at the
      // device for all of its features, very much like the hardware feature check above. Just checking the AVD to see if it was
      // created with the same addon target is somewhat of a legacy method that we use for now..
      return isCompatibleAddonAvd(projectTarget, avdTarget);
    }
  }

  private static LaunchCompatibility isCompatibleAddonAvd(IAndroidTarget projectTarget, IAndroidTarget avdTarget) {
    // validate that the vendor is the same for both the project and the avd
    if (!StringUtil.equals(projectTarget.getVendor(), avdTarget.getVendor())) {
      String reason = String.format("AVD vendor (%1$s) != AVD target (%2$s)", avdTarget.getVendor(), projectTarget.getVendor());
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    if (!StringUtil.equals(projectTarget.getName(), avdTarget.getName())) {
      String reason =
        String.format("AVD target name (%1$s) != Project target name (%2$s)", avdTarget.getName(), projectTarget.getName());
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    return YES;
  }

  /** Returns whether a project with given minSdkVersion and target platform can be run on an AVD with given target platform. */
  @NotNull
  public static LaunchCompatibility canRunOnAvd(@NotNull AndroidVersion minSdkVersion,
                                                @NotNull IAndroidTarget projectTarget,
                                                @NotNull IAndroidTarget avdTarget) {
    AndroidVersion avdVersion = avdTarget.getVersion();
    if (!avdVersion.canRun(minSdkVersion)) {
      String reason = String.format("minSdk(%1$s) %3$s deviceSdk(%2$s)",
                                    minSdkVersion,
                                    avdVersion,
                                    minSdkVersion.getCodename() == null ? ">" : "!=");
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    return projectTarget.isPlatform() ? YES : isCompatibleAddonAvd(projectTarget, avdTarget);
  }
}
