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
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ConnectedDevice extends Device {
  static class Builder extends Device.Builder {
    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    final Builder setValid(boolean valid) {
      myValid = valid;
      return this;
    }

    @NotNull
    final Builder setValidityReason(@Nullable String validityReason) {
      myValidityReason = validityReason;
      return this;
    }

    @NotNull
    Builder setKey(@NotNull String key) {
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

  @VisibleForTesting
  ConnectedDevice(@NotNull Builder builder) {
    super(builder);
  }

  @Nullable
  String getVirtualDeviceKey() {
    return Objects.requireNonNull(getDdmlibDevice()).getAvdName();
  }

  @NotNull
  String getPhysicalDeviceName(@NotNull DeviceNamePropertiesFetcher fetcher) {
    return getName(fetcher.get(Objects.requireNonNull(getDdmlibDevice())));
  }

  @NotNull
  @VisibleForTesting
  static String getName(@NotNull DeviceNameProperties properties) {
    String manufacturer = properties.getManufacturer();
    String model = properties.getModel();

    if (manufacturer == null && model == null) {
      return "Unknown Device";
    }

    if (manufacturer == null) {
      return model;
    }

    if (model == null) {
      return manufacturer + " Device";
    }

    return manufacturer + ' ' + model;
  }

  @NotNull
  @Override
  final Icon getIcon() {
    throw new UnsupportedOperationException();
  }

  @Override
  final boolean isConnected() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  final Collection<Snapshot> getSnapshots() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  final Future<AndroidVersion> getAndroidVersion() {
    throw new UnsupportedOperationException();
  }

  @Override
  final void addTo(@NotNull DeviceFutures futures, @NotNull Project project, @Nullable Snapshot snapshot) {
    throw new UnsupportedOperationException();
  }
}
