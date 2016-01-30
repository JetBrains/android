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
import com.android.sdklib.ISystemImage;
import com.google.common.base.Objects;
import com.intellij.ide.util.treeView.TreeState;
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

  public static final LaunchCompatibility YES = new LaunchCompatibility(ThreeState.YES, null);

  public LaunchCompatibility(ThreeState compatible, @Nullable String reason) {
    myCompatible = compatible;
    myReason = reason;
  }

  public LaunchCompatibility combine(@NotNull LaunchCompatibility other) {
    if (myCompatible == ThreeState.NO) {
      return this;
    }
    if (other.myCompatible == ThreeState.NO) {
      return other;
    }
    if (myCompatible == ThreeState.UNSURE) {
      return this;
    }
    return other;
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

  /**
   * Returns whether an application with the given requirements can be run on the given device.
   *
   * @param minSdkVersion    minSdkVersion specified by the application
   * @param projectTarget    android target corresponding to the targetSdkVersion
   * @param requiredFeatures required list of hardware features
   * @param device           the device to check compatibility against
   * @return a {@link ThreeState} indicating whether the application can be run on the device, and a reason if it isn't
   * compatible.
   */
  @NotNull
  public static LaunchCompatibility canRunOnDevice(@NotNull AndroidVersion minSdkVersion,
                                                   @NotNull IAndroidTarget projectTarget,
                                                   @NotNull EnumSet<IDevice.HardwareFeature> requiredFeatures,
                                                   @NotNull AndroidDevice device) {
    // check if the device has the required minApi
    // note that in cases where targetSdk is a preview platform, gradle sets minsdk to be the same as targetsdk,
    // so as to only allow running on those systems
    AndroidVersion deviceVersion = device.getVersion();
    if (!deviceVersion.equals(AndroidVersion.DEFAULT) && !deviceVersion.canRun(minSdkVersion)) {
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
    List<IAndroidTarget.OptionalLibrary> additionalLibs = projectTarget.getAdditionalLibraries();
    if (additionalLibs.isEmpty()) {
      return YES;
    }

    String targetName = projectTarget.getName();
    if (GOOGLE_APIS_TARGET_NAME.equals(targetName)) {
      // We'll assume that Google APIs are available on all devices.
      return YES;
    } else {
      // Unsure because we don't have an easy way of determining whether those libraries are on a device
      return new LaunchCompatibility(ThreeState.UNSURE, "unsure if device supports addon: " + targetName);
    }
  }

  private static LaunchCompatibility isCompatibleAddonAvd(IAndroidTarget projectTarget, ISystemImage image) {
    // validate that the vendor is the same for both the project and the avd
    if (!StringUtil.equals(projectTarget.getVendor(), image.getAddonVendor().getDisplay())) {
      String reason =
        String.format("AVD vendor (%1$s) != AVD target (%2$s)", image.getAddonVendor().getDisplay(), projectTarget.getVendor());
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    if (!StringUtil.equals(projectTarget.getName(), image.getTag().getDisplay())) {
      String reason =
        String.format("AVD target name (%1$s) != Project target name (%2$s)", image.getTag().getDisplay(), projectTarget.getName());
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    return YES;
  }

  /** Returns whether a project with given minSdkVersion and target platform can be run on an AVD with given target platform. */
  @NotNull
  public static LaunchCompatibility canRunOnAvd(@NotNull AndroidVersion minSdkVersion,
                                                @NotNull IAndroidTarget projectTarget,
                                                @NotNull ISystemImage image) {
    AndroidVersion avdVersion = image.getAndroidVersion();
    if (!avdVersion.canRun(minSdkVersion)) {
      String reason = String.format("minSdk(%1$s) %3$s deviceSdk(%2$s)",
                                    minSdkVersion,
                                    avdVersion,
                                    minSdkVersion.getCodename() == null ? ">" : "!=");
      return new LaunchCompatibility(ThreeState.NO, reason);
    }

    return projectTarget.isPlatform() ? YES : isCompatibleAddonAvd(projectTarget, image);
  }
}
