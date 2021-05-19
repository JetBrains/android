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
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Function;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice extends Device {
  private static final Icon ourValidIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE);
  private static final Icon ourInvalidIcon = ExecutionUtil.getLiveIndicator(AllIcons.General.Error);

  @NotNull
  static PhysicalDevice newDevice(@NotNull ConnectedDevice device,
                                  @NotNull Function<ConnectedDevice, String> getName,
                                  @NotNull KeyToConnectionTimeMap map) {
    Key key = device.getKey();

    return new Builder()
      .setName(getName.apply(device))
      .setValid(device.isValid())
      .setValidityReason(device.getValidityReason())
      .setKey(key)
      .setConnectionTime(map.get(key))
      .setAndroidDevice(device.getAndroidDevice())
      .build();
  }

  @VisibleForTesting
  static final class Builder extends Device.Builder {
    @NotNull
    @VisibleForTesting
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    private Builder setValid(boolean valid) {
      myValid = valid;
      return this;
    }

    @NotNull
    private Builder setValidityReason(@Nullable String validityReason) {
      myValidityReason = validityReason;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setKey(@NotNull Key key) {
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
    @VisibleForTesting
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    @Override
    PhysicalDevice build() {
      return new PhysicalDevice(this);
    }
  }

  private PhysicalDevice(@NotNull Builder builder) {
    super(builder);
  }

  @NotNull
  @Override
  Icon getIcon() {
    return isValid() ? ourValidIcon : ourInvalidIcon;
  }

  /**
   * @return true. Physical devices come and go as they are connected and disconnected; there are no instances of this class for
   * disconnected physical devices.
   */
  @Override
  boolean isConnected() {
    return true;
  }

  @Nullable
  @Override
  Snapshot getSnapshot() {
    return null;
  }

  @Override
  boolean matches(@NotNull Key key) {
    return getKey().matches(key);
  }

  @Override
  boolean hasKeyContainedBy(@NotNull Collection<@NotNull Key> keys) {
    return keys.contains(getKey()) || keys.contains(getKey().asNonprefixedKey());
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    IDevice device = getDdmlibDevice();
    assert device != null;

    return DeploymentApplicationService.getInstance().getVersion(device);
  }

  @Override
  void addTo(@NotNull DeviceFutures futures, @NotNull Project project) {
    futures.getDevices().add(getAndroidDevice());
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    Device device = (Device)object;

    return getName().equals(device.getName()) &&
           isValid() == device.isValid() &&
           Objects.equals(getValidityReason(), device.getValidityReason()) &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getAndroidDevice().equals(device.getAndroidDevice());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), isValid(), getValidityReason(), getKey(), getConnectionTime(), getAndroidDevice());
  }
}
