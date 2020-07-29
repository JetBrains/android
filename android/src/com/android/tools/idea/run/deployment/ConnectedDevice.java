/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ConnectedDevice extends Device {
  static final class Builder extends Device.Builder {
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
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    @Override
    ConnectedDevice build() {
      return new ConnectedDevice(this);
    }
  }

  private ConnectedDevice(@NotNull Builder builder) {
    super(builder);
  }

  boolean isVirtualDevice() {
    return getAndroidDevice().isVirtual();
  }

  boolean isPhysicalDevice() {
    return !getAndroidDevice().isVirtual();
  }

  @NotNull
  @Override
  Icon getIcon() {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean isConnected() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  Snapshot getSnapshot() {
    return null;
  }

  @Override
  boolean matches(@NotNull Key key) {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean hasKeyContainedBy(@NotNull Collection<@NotNull Key> keys) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  Future<AndroidVersion> getAndroidVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  void addTo(@NotNull DeviceFutures futures, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof ConnectedDevice)) {
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
    return Objects.hash(getName(),
                        isValid(),
                        getValidityReason(),
                        getKey(),
                        getConnectionTime(),
                        getAndroidDevice());
  }
}
