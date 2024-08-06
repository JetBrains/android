/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.run;

import static com.android.sdklib.internal.avd.ConfigKey.DEVICE_NAME;
import static com.android.sdklib.internal.avd.UserSettingsKey.PREFERRED_ABI;
import static com.android.tools.idea.avdmanager.AvdManagerConnection.getDefaultAvdManagerConnection;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeAvdDevice implements AndroidDevice {
  private static final Map<Abi, List<Abi>> ABI_MAPPINGS = ImmutableMap.of(
    Abi.X86_64, ImmutableList.of(Abi.X86_64, Abi.X86),
    Abi.ARM64_V8A, ImmutableList.of(Abi.ARM64_V8A, Abi.ARMEABI_V7A, Abi.ARMEABI));
  @NotNull private final AvdInfo myAvdInfo;

  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private ListenableFuture<IDevice> myLaunchedEmulator;

  public FakeAvdDevice(@NotNull AvdInfo avdInfo) {
    myAvdInfo = avdInfo;
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public boolean isVirtual() {
    return true;
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    return myAvdInfo.getAndroidVersion();
  }

  @Override
  public int getDensity() {
    String s = myAvdInfo.getProperties().get(HardwareProperties.HW_LCD_DENSITY);
    if (s == null) {
      return -1;
    }

    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  @Override
  public boolean supportsMultipleScreenFormats() {
    return myAvdInfo.getAndroidVersion().isGreaterOrEqualThan(AndroidVersion.MIN_RESIZABLE_DEVICE_API)
           && "resizable".equals(myAvdInfo.getProperties().get(DEVICE_NAME));
  }

  @NotNull
  @Override
  public List<Abi> getAbis() {
    Abi abi = Abi.getEnum(myAvdInfo.getAbiType());
    if (abi == null) {
      return Collections.emptyList();
    }
    List<Abi> abis = ABI_MAPPINGS.get(abi);
    if (abis != null) {
      return abis;
    }
    return Collections.singletonList(abi);
  }

  @Nullable
  @Override
  public String getAppPreferredAbi() {
    return myAvdInfo.getUserSettings().get(PREFERRED_ABI);
  }

  @NotNull
  @Override
  public String getSerial() {
    return "AVD: " +  myAvdInfo.getId();
  }

  @Override
  public boolean supportsFeature(@NotNull IDevice.HardwareFeature feature) {
    return switch (feature) {
      case WATCH -> SystemImageTags.isWearImage(myAvdInfo.getTags());
      case TV -> SystemImageTags.isTvImage(myAvdInfo.getTags());
      case AUTOMOTIVE -> SystemImageTags.isAutomotiveImage(myAvdInfo.getTags());
      default -> true;
    };
  }

  @Override
  public boolean getSupportsSdkRuntime() {
    return getVersion().isGreaterOrEqualThan(34);
  }

  @NotNull
  @Override
  public String getName() {
    return myAvdInfo.getDisplayName();
  }

  public void coldBoot(@NotNull Project project) {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        myLaunchedEmulator = getDefaultAvdManagerConnection().coldBoot(project, myAvdInfo, RequestType.INDIRECT);
      }
    }
  }

  public void quickBoot(@NotNull Project project) {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        myLaunchedEmulator = getDefaultAvdManagerConnection().quickBoot(project, myAvdInfo, RequestType.INDIRECT);
      }
    }
  }

  public void bootWithSnapshot(@NotNull Project project, @NotNull String snapshot) {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        myLaunchedEmulator = getDefaultAvdManagerConnection().bootWithSnapshot(project, myAvdInfo, snapshot, RequestType.INDIRECT);
      }
    }
  }

  @NotNull
  @Override
  public ListenableFuture<IDevice> getLaunchedDevice() {
    synchronized (myLock) {
      if (myLaunchedEmulator == null) {
        throw new IllegalStateException("Attempt to get device corresponding to an emulator that hasn't been launched yet.");
      }

      return myLaunchedEmulator;
    }
  }

  @Override
  @NotNull
  public LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                                    @NotNull IAndroidTarget projectTarget,
                                    @NotNull Supplier<EnumSet<IDevice.HardwareFeature>> getRequiredHardwareFeatures,
                                    @NonNull Set<Abi> supportedAbis) {
    LaunchCompatibility compatibility = LaunchCompatibility.YES;

    if (myAvdInfo.getStatus() != AvdInfo.AvdStatus.OK) {
      if (myAvdInfo.getStatus().equals(AvdInfo.AvdStatus.ERROR_IMAGE_MISSING)) {
        // The original error message includes the name of the AVD which is already shown in the UI.
        // Make the error message simpler here:
        compatibility = new LaunchCompatibility(LaunchCompatibility.State.ERROR, "Missing system image");
      }
      else {
        compatibility = new LaunchCompatibility(LaunchCompatibility.State.ERROR, myAvdInfo.getErrorMessage());
      }
    }
    return compatibility
      .combine(LaunchCompatibility.canRunOnDevice(minSdkVersion, projectTarget, getRequiredHardwareFeatures, supportedAbis, this));
  }

  @NotNull
  public AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @Override
  public boolean isDebuggable() {
    return !myAvdInfo.hasPlayStore();
  }
}
