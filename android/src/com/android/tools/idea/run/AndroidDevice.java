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
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * An {@link AndroidDevice} represents either a connected {@link IDevice}, or the
 * {@link com.android.sdklib.internal.avd.AvdInfo} corresponding to an emulator that can be launched.
 */
public interface AndroidDevice {
  /** Returns whether the device is currently running. */
  boolean isRunning();

  /** Returns whether this is a virtual device. */
  boolean isVirtual();

  /** Returns the API level of the device. */
  @NotNull
  AndroidVersion getVersion();

  /** Returns the device display density. */
  int getDensity();

  /** Returns the list of (sorted by most preferred first) ABIs supported by this device. */
  @NotNull
  List<Abi> getAbis();

  /** Returns a unique serial number */
  @NotNull
  String getSerial();

  /** Returns whether this device supports the given hardware feature. */
  boolean supportsFeature(@NotNull IDevice.HardwareFeature feature);

  /** Returns the device name. */
  @NotNull
  String getName();

  /** Renders the device name and misc. info to the given component. */
  void renderName(@NotNull SimpleColoredComponent renderer, boolean isCompatible, @Nullable String searchPrefix);

  /** Returns the {@link IDevice} corresponding to this device, launching it if necessary. */
  @NotNull
  ListenableFuture<IDevice> launch(@NotNull Project project);

  /**
   * Returns the {@link IDevice} corresponding to this device if it is running or has been launched.
   * Throws {@link IllegalStateException} if the device is not running and hasn't been launched.
   */
  @NotNull
  ListenableFuture<IDevice> getLaunchedDevice();

  /** Check if this device can run an application with given requirements. */
  @NotNull
  LaunchCompatibility canRun(@NotNull AndroidVersion minSdkVersion,
                             @NotNull IAndroidTarget projectTarget,
                             @NotNull EnumSet<IDevice.HardwareFeature> requiredFeatures,
                             @Nullable Set<String> supportedAbis);
}
