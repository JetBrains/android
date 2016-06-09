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
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.androidTarget.MockAddonTarget;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.util.ThreeState;
import junit.framework.TestCase;
import org.fest.util.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LaunchCompatibilityTest extends TestCase {
  public void testMinSdk() {
    final MockPlatformTarget projectTarget = new MockPlatformTarget(14, 0);
    final EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);

    // cannot run if the API level of device is < API level required by minSdk
    LaunchCompatibility compatibility =
      LaunchCompatibility.canRunOnDevice(new AndroidVersion(8, null), projectTarget, requiredFeatures, null, createMockDevice(7, null));
    assertEquals(new LaunchCompatibility(ThreeState.NO, "minSdk(API 8) > deviceSdk(API 7)"), compatibility);

    // can run if the API level of device is >= API level required by minSdk
    compatibility =
      LaunchCompatibility.canRunOnDevice(new AndroidVersion(8, null), projectTarget, requiredFeatures, null, createMockDevice(8, null));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);

    // cannot run if minSdk uses a code name that is not matched by the device
    compatibility =
      LaunchCompatibility.canRunOnDevice(new AndroidVersion(8, "P"), projectTarget, requiredFeatures, null, createMockDevice(9, null));
    assertEquals(new LaunchCompatibility(ThreeState.NO, "minSdk(API 8, P preview) != deviceSdk(API 9)"), compatibility);
  }

  public void testRequiredDeviceCharacteristic() {
    final AndroidVersion minSdkVersion = new AndroidVersion(8, null);
    final MockPlatformTarget projectTarget = new MockPlatformTarget(14, 0);
    EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.of(IDevice.HardwareFeature.WATCH);

    // cannot run if the device doesn't have a required feature
    LaunchCompatibility compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, null, createMockDevice(8, null, false));
    assertEquals(new LaunchCompatibility(ThreeState.NO, "missing feature: WATCH"), compatibility);

    // can run if the device has the required features
    compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, null, createMockDevice(8, null, true));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);

    // cannot run apk's that don't specify uses-feature watch on a wear device
    requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, null, createMockDevice(8, null, true));
    assertEquals(new LaunchCompatibility(ThreeState.NO, "missing uses-feature watch, non-watch apks cannot be launched on a watch"),
                 compatibility);
  }

  public void testRequiredAddons() {
    final AndroidVersion minSdkVersion = new AndroidVersion(8, null);
    final EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);

    // add-on target shouldn't affect anything if it doesn't have optional libraries
    final MockPlatformTarget baseTarget = new MockPlatformTarget(14, 0);
    MockAddonTarget projectTarget = new MockAddonTarget("google", baseTarget, 1);
    LaunchCompatibility compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, null, createMockDevice(8, null, false));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);

    IAndroidTarget.OptionalLibrary optionalLibrary = mock(IAndroidTarget.OptionalLibrary.class);
    projectTarget.setOptionalLibraries(ImmutableList.of(optionalLibrary));

    // add-on targets with optional libraries should still be allowed to run on real devices (no avdinfo)
    compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures, null, createMockDevice(8, null, false));
    assertEquals(new LaunchCompatibility(ThreeState.UNSURE, "unsure if device supports addon: google"), compatibility);

    // Google APIs add on should be treated as a special case and should always be allowed to run on a real device
    MockAddonTarget googleApiTarget = new MockAddonTarget("Google APIs", baseTarget, 1);
    googleApiTarget.setOptionalLibraries(ImmutableList.of(optionalLibrary));
    compatibility =
      LaunchCompatibility.canRunOnDevice(minSdkVersion, googleApiTarget, requiredFeatures, null, createMockDevice(8, null, false));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);
  }

  public void testCompatibleAbiFilter() {
    AndroidVersion minSdkVersion = new AndroidVersion(8, null);
    MockPlatformTarget projectTarget = new MockPlatformTarget(14, 0);
    EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    Set<String> supportedAbis = Sets.newLinkedHashSet(Collections.singletonList(Abi.ARMEABI_V7A.toString()));

    List<Abi> deviceAbis = Lists.newArrayList(Abi.ARMEABI, Abi.ARMEABI_V7A, Abi.ARM64_V8A);
    LaunchCompatibility compatibility = LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures,
                                                                           supportedAbis, createMockDevice(8, null, false, deviceAbis));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);
  }

  public void testIncompatibleAbiFilter() {
    AndroidVersion minSdkVersion = new AndroidVersion(8, null);
    MockPlatformTarget projectTarget = new MockPlatformTarget(14, 0);
    EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    Set<String> supportedAbis = Sets.newLinkedHashSet(Collections.singleton(Abi.X86_64.toString()));

    List<Abi> deviceAbis = Lists.newArrayList(Abi.ARMEABI, Abi.ARMEABI_V7A, Abi.ARM64_V8A);
    LaunchCompatibility compatibility = LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures,
                                                                           supportedAbis, createMockDevice(8, null, false, deviceAbis));
    assertEquals(new LaunchCompatibility(ThreeState.NO, "Device supports armeabi, armeabi-v7a, arm64-v8a, but APK only supports x86_64"),
                 compatibility);
  }

  public void testOpenAbiFilter() {
    AndroidVersion minSdkVersion = new AndroidVersion(8, null);
    MockPlatformTarget projectTarget = new MockPlatformTarget(14, 0);
    EnumSet<IDevice.HardwareFeature> requiredFeatures = EnumSet.noneOf(IDevice.HardwareFeature.class);
    Set<String> supportedAbis = Sets.newLinkedHashSet(Collections.emptyList());

    List<Abi> deviceAbis = Lists.newArrayList(Abi.ARMEABI, Abi.ARMEABI_V7A, Abi.ARM64_V8A);
    LaunchCompatibility compatibility = LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, requiredFeatures,
                                                                           supportedAbis, createMockDevice(8, null, false, deviceAbis));
    assertEquals(new LaunchCompatibility(ThreeState.YES, null), compatibility);
  }

  private static AndroidDevice createMockDevice(int api, @Nullable String codeName) {
    return createMockDevice(api, codeName, false);
  }

  private static AndroidDevice createMockDevice(int api, @Nullable String codeName, boolean supportsFeature) {
    return createMockDevice(api, codeName, supportsFeature, Lists.emptyList());
  }

  private static AndroidDevice createMockDevice(int api, @Nullable String codeName, boolean supportsFeature, List<Abi> abis) {
    AndroidDevice device = mock(AndroidDevice.class);
    try {
      when(device.getVersion()).thenReturn(new AndroidVersion(api, codeName));
      when(device.supportsFeature(any(IDevice.HardwareFeature.class))).thenReturn(supportsFeature);
      when(device.getAbis()).thenReturn(abis);
    }
    catch (Exception ignored) {
    }
    return device;
  }
}
