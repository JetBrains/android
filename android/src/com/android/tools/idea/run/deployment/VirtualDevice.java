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
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchableAndroidDevice;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevice extends Device {
  static final ImmutableCollection<Snapshot> DEFAULT_SNAPSHOT_COLLECTION = ImmutableList.of(Snapshot.DEFAULT);
  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);

  @Nullable
  private final Snapshot mySnapshot;

  @NotNull
  static VirtualDevice newConnectedDevice(@NotNull VirtualDevice virtualDevice,
                                          @NotNull ConnectedDevice connectedDevice,
                                          @NotNull KeyToConnectionTimeMap map) {
    String key = virtualDevice.getKey();

    return new Builder()
      .setName(virtualDevice.getName())
      .setValid(connectedDevice.isValid())
      .setValidityReason(connectedDevice.getValidityReason())
      .setKey(key)
      .setConnectionTime(map.get(key))
      .setAndroidDevice(connectedDevice.getAndroidDevice())
      .setSnapshot(virtualDevice.mySnapshot)
      .build();
  }

  static final class Builder extends Device.Builder {
    @Nullable
    private Snapshot mySnapshot;

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    Builder setValid(boolean valid) {
      myValid = valid;
      return this;
    }

    @NotNull
    Builder setValidityReason(@Nullable String validityReason) {
      myValidityReason = validityReason;
      return this;
    }

    @NotNull
    Builder setKey(@NotNull String key) {
      myKey = key;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setConnectionTime(@NotNull Instant connectionTime) {
      myConnectionTime = connectionTime;
      return this;
    }

    @NotNull
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    Builder setSnapshot(@Nullable Snapshot snapshot) {
      mySnapshot = snapshot;
      return this;
    }

    @NotNull
    @Override
    VirtualDevice build() {
      return new VirtualDevice(this);
    }
  }

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);
    mySnapshot = builder.mySnapshot;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return isConnected() ? ourConnectedIcon : StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
  }

  @Override
  boolean isConnected() {
    return getConnectionTime() != null;
  }

  @NotNull
  @Override
  ImmutableCollection<Snapshot> getSnapshots() {
    // TODO Delete Device::getSnapshots?
    return mySnapshot == null ? ImmutableList.of() : ImmutableList.of(mySnapshot);
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    Object androidDevice = getAndroidDevice();

    if (androidDevice instanceof LaunchableAndroidDevice) {
      // noinspection UnstableApiUsage
      return Futures.immediateFuture(((LaunchableAndroidDevice)androidDevice).getAvdInfo().getAndroidVersion());
    }

    IDevice ddmlibDevice = getDdmlibDevice();
    assert ddmlibDevice != null;

    return DeploymentApplicationService.getInstance().getVersion(ddmlibDevice);
  }

  @Override
  void addTo(@NotNull DeviceFutures futures, @NotNull Project project, @Nullable Snapshot snapshot) {
    AndroidDevice device = getAndroidDevice();

    if (!isConnected()) {
      device.launch(project, snapshot == null ? null : snapshot.getDirectoryName());
    }

    futures.getDevices().add(device);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return getName().equals(device.getName()) &&
           isValid() == device.isValid() &&
           Objects.equals(getValidityReason(), device.getValidityReason()) &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice()) &&
           Objects.equals(mySnapshot, device.mySnapshot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      getName(),
      isValid(),
      getValidityReason(),
      getKey(),
      getConnectionTime(),
      getAndroidDevice(),
      mySnapshot);
  }
}
