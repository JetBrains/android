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
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.deployable.Deployable;
import com.intellij.openapi.project.Project;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Device {
  @NotNull
  private final String myName;

  private final boolean myValid;

  @Nullable
  private final String myValidityReason;

  @NotNull
  private final Key myKey;

  @Nullable
  private final Instant myConnectionTime;

  @NotNull
  private final AndroidDevice myAndroidDevice;

  static abstract class Builder {
    @Nullable
    String myName;

    boolean myValid;

    @Nullable
    String myValidityReason;

    @Nullable
    Key myKey;

    @Nullable
    Instant myConnectionTime;

    @Nullable
    AndroidDevice myAndroidDevice;

    Builder() {
      myValid = true;
    }

    @NotNull
    abstract Device build();
  }

  Device(@NotNull Builder builder) {
    assert builder.myName != null;
    myName = builder.myName;

    myValid = builder.myValid;
    myValidityReason = builder.myValidityReason;

    assert builder.myKey != null;
    myKey = builder.myKey;

    myConnectionTime = builder.myConnectionTime;

    assert builder.myAndroidDevice != null;
    myAndroidDevice = builder.myAndroidDevice;
  }

  @NotNull
  abstract Icon getIcon();

  abstract boolean isConnected();

  @NotNull
  public final String getName() {
    return myName;
  }

  final boolean isValid() {
    return myValid;
  }

  @Nullable
  final String getValidityReason() {
    return myValidityReason;
  }

  @Nullable
  abstract Snapshot getSnapshot();

  @NotNull
  public final Key getKey() {
    return myKey;
  }

  abstract boolean matches(@NotNull Key key);

  abstract boolean hasKeyContainedBy(@NotNull Collection<@NotNull Key> keys);

  @Nullable
  final Instant getConnectionTime() {
    return myConnectionTime;
  }

  @NotNull
  final AndroidDevice getAndroidDevice() {
    return myAndroidDevice;
  }

  @NotNull
  abstract Future<AndroidVersion> getAndroidVersion();

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

    if (!device.isRunning()) {
      return null;
    }

    try {
      return device.getLaunchedDevice().get();
    }
    catch (InterruptedException | ExecutionException exception) {
      throw new AssertionError(exception);
    }
  }

  abstract void addTo(@NotNull DeviceFutures futures, @NotNull Project project);

  @NotNull
  @Override
  public final String toString() {
    return myName;
  }
}
