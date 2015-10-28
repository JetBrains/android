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

import com.android.annotations.NonNull;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.intellij.ui.ColoredTextContainer;
import org.jetbrains.annotations.NotNull;

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

  /** Returns a unique serial number */
  @NotNull
  String getSerial();

  /** Returns whether this device supports the given hardware feature. */
  boolean supportsFeature(@NonNull IDevice.HardwareFeature feature);

  /** Renders the device name to the given component. */
  void renderName(@NotNull ColoredTextContainer component);
}
