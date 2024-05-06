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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.devices.Abi;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.Function;
import com.intellij.util.ThreeState;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchCompatibility {
  public static final LaunchCompatibility YES = new LaunchCompatibility(State.OK, null);

  @NonNls private static final String GOOGLE_APIS_TARGET_NAME = "Google APIs";

  private final String myReason;
  private final State myState;

  public LaunchCompatibility(@NotNull State state, @Nullable String reason) {
    myReason = reason;
    myState = state;
  }

  public LaunchCompatibility combine(@NotNull LaunchCompatibility other) {
    if (myState == State.ERROR) {
      return this;
    }
    if (other.myState == State.ERROR) {
      return other;
    }
    if (myState == State.WARNING) {
      return this;
    }
    return other;
  }

  @Nullable
  public String getReason() {
    return myReason;
  }

  public @NotNull State getState() {
    return myState;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("state", myState).add("reason", myReason).toString();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof LaunchCompatibility &&
           myState == ((LaunchCompatibility)o).myState &&
           Objects.equal(myReason, ((LaunchCompatibility)o).myReason);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myState, myReason);
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
                                                   @NotNull AndroidFacet facet,
                                                   Function<AndroidFacet, EnumSet<IDevice.HardwareFeature>> getRequiredHardwareFeatures,
                                                   @NotNull Set<Abi> supportedAbis,
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
      return new LaunchCompatibility(State.WARNING, reason);
    }

    EnumSet<IDevice.HardwareFeature> requiredFeatures;
    try {
      requiredFeatures = getRequiredHardwareFeatures.fun(facet);
    }
    catch (IndexNotReadyException e) {
      return new LaunchCompatibility(State.ERROR, "Required features are unsure because indices are not ready.");
    }

    // check if the device provides the required features
    for (IDevice.HardwareFeature feature : requiredFeatures) {
      if (!device.supportsFeature(feature)) {
        return new LaunchCompatibility(State.WARNING, "missing feature: " + feature);
      }
    }

    // Typically, we only need to check that features required by the apk are supported by the device, which is done above
    // In the case of watch though, we do an explicit check in the other direction: if the device is a watch, we don't want
    // non-watch apks to be installed on it.
    if (device.supportsFeature(IDevice.HardwareFeature.WATCH)) {
      if (!requiredFeatures.contains(IDevice.HardwareFeature.WATCH)) {
        return new LaunchCompatibility(State.WARNING,
                                       "missing uses-feature watch, non-watch apks cannot be launched on a watch");
      }
    }

    // Verify that the device ABI matches one of the target ABIs for JNI apps.
    if (!supportedAbis.isEmpty()) {
      Set<Abi> deviceAbis = Sets.newLinkedHashSet();
      deviceAbis.addAll(device.getAbis());

      if (Sets.intersection(supportedAbis, deviceAbis).isEmpty()) {
        return new LaunchCompatibility(State.WARNING, "Device supports " + Joiner.on(", ").join(deviceAbis) +
                                                      ", but APK only supports " + Joiner.on(", ").join(supportedAbis));
      }
    }

    // we are done with checks for platform targets
    if (projectTarget.isPlatform()) {
      return YES;
    }

    // Add-ons specify a list of libraries. We need to check that the required libraries are available on the device.
    // See AddOnTarget#canRunOn
    List<OptionalLibrary> additionalLibs = projectTarget.getAdditionalLibraries();
    if (additionalLibs.isEmpty()) {
      return YES;
    }

    String targetName = projectTarget.getName();
    if (GOOGLE_APIS_TARGET_NAME.equals(targetName)) {
      // We'll assume that Google APIs are available on all devices.
      return YES;
    }
    else {
      // Unsure because we don't have an easy way of determining whether those libraries are on a device
      return new LaunchCompatibility(State.ERROR, "unsure if device supports addon: " + targetName);
    }
  }

  public enum State {
    OK,
    WARNING,
    ERROR
  }
}
