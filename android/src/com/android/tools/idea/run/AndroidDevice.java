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
import com.android.sdklib.devices.Abi;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.project.Project;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link AndroidDevice} represents either a connected {@link IDevice}, or the
 * {@link com.android.sdklib.internal.avd.AvdInfo} corresponding to an emulator that can be launched.
 */
public interface AndroidDevice {
  /**
   * Returns whether the device is currently running.
   */
  boolean isRunning();

  /**
   * Returns whether this is a virtual device.
   */
  boolean isVirtual();

  /**
   * Returns whether this is a remote device.
   */
  default boolean isRemote() {
    return false;
  }

  /**
   * Returns the API level of the device.
   */
  @NotNull
  AndroidVersion getVersion();

  /**
   * Returns the device display density.
   */
  int getDensity();

  /**
   * Returns true if multiple screens setups are supported.
   */
  default boolean supportsMultipleScreenFormats() {
    return false;
  }

  /**
   * Returns the list of (sorted by most preferred first) ABIs supported by this device.
   */
  @NotNull
  List<Abi> getAbis();

  /**
   * Returns the ABI that should be used for apps deployed to this device.
   * This can be used to test apps built with a non-native (translated) ABI.
   * If null, the ABI used will be chosen by the build system from among the list returned by {@link #getAbis()}.
   */
  @Nullable
  String getAppPreferredAbi();

  /**
   * Returns a unique, opaque, identifier for the device, which should be constant even when starting and stopping the underlying device.
   * <p>
   * Note this may not be equal to the adb serial for devices that can be started.
   */
  @NotNull
  String getSerial();

  /**
   * Returns whether this device supports the given hardware feature.
   */
  boolean supportsFeature(@NotNull IDevice.HardwareFeature feature);

  /** Whether the device supports running SDKs in the Privacy Sandbox */
  default boolean getSupportsSdkRuntime() {
    return false;
  }
  /**
   * Returns the device name.
   */
  @NotNull
  String getName();

  /**
   * Returns the {@link IDevice} corresponding to this device, launching it if necessary.
   */
  @NotNull
  ListenableFuture<IDevice> launch(@NotNull Project project);

  /**
   * Returns the {@link IDevice} corresponding to this device if it is running or has been launched.
   * Throws {@link IllegalStateException} if the device is not running and hasn't been launched.
   */
  @NotNull
  ListenableFuture<IDevice> getLaunchedDevice();

  /**
   * Check if this device can run an application with given requirements.
   */
  @NotNull
  LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                             @NotNull IAndroidTarget projectTarget,
                             @NotNull Supplier<EnumSet<IDevice.HardwareFeature>> getRequiredHardwareFeatures,
                             @NotNull Set<Abi> supportedAbis);

  /**
   * Returns whether this device is debuggable or not.
   */
  boolean isDebuggable();

  @Nullable
  default Icon getIcon() {
    return null;
  }
}
