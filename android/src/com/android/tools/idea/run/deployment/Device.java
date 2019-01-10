/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import com.android.tools.idea.run.deployable.Deployable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import java.time.Instant;
import java.util.Collection;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class Device {
  @NotNull
  private final String myName;

  private final boolean myIsValid;

  @NotNull
  private final String myKey;

  @Nullable
  private final Instant myConnectionTime;

  @NotNull
  private final AndroidDevice myAndroidDevice;

  static abstract class Builder<T extends Builder<T>> {
    @Nullable
    private String myName;

    @Nullable
    private String myKey;

    @Nullable
    private AndroidDevice myAndroidDevice;

    @NotNull
    final T setName(@NotNull String name) {
      myName = name;
      return self();
    }

    @NotNull
    final T setKey(@NotNull String key) {
      myKey = key;
      return self();
    }

    @NotNull
    final T setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return self();
    }

    @NotNull
    abstract T self();

    @NotNull
    abstract Device build(@Nullable LaunchCompatibilityChecker checker, @NotNull ConnectionTimeService service);
  }

  Device(@NotNull Builder builder, @Nullable LaunchCompatibilityChecker checker, @NotNull ConnectionTimeService service) {
    assert builder.myName != null;
    myName = builder.myName;

    assert builder.myKey != null;
    myKey = builder.myKey;

    assert builder.myAndroidDevice != null;
    myAndroidDevice = builder.myAndroidDevice;

    myIsValid = checker == null || !checker.validate(myAndroidDevice).isCompatible().equals(ThreeState.NO);
    myConnectionTime = service.get(myKey);
  }

  @NotNull
  abstract Icon getIcon();

  abstract boolean isConnected();

  @NotNull
  final String getName() {
    return myName;
  }

  final boolean isValid() {
    return myIsValid;
  }

  @NotNull
  abstract Collection<String> getSnapshots();

  @NotNull
  final String getKey() {
    return myKey;
  }

  @Nullable
  final Instant getConnectionTime() {
    return myConnectionTime;
  }

  @NotNull
  final AndroidDevice getAndroidDevice() {
    return myAndroidDevice;
  }

  @NotNull
  abstract AndroidVersion getAndroidVersion();

  final boolean isRunning(@NotNull String appPackage) {
    if (!isConnected()) {
      return false;
    }

    IDevice device = getDdmlibDevice();
    assert device != null;

    if (!device.isOnline()) {
      return false;
    }

    return !Deployable.searchClientsForPackage(device, appPackage).isEmpty();
  }

  @Nullable
  final IDevice getDdmlibDevice() {
    AndroidDevice device = getAndroidDevice();

    // TODO(b/122324579) Add a getDevice method to AndroidDevice
    if (!(device instanceof ConnectedAndroidDevice)) {
      return null;
    }

    return ((ConnectedAndroidDevice)device).getDevice();
  }

  @NotNull
  abstract DeviceFutures newDeviceFutures(@NotNull Project project, @Nullable String snapshot);

  @NotNull
  @Override
  public final String toString() {
    return myName;
  }
}
