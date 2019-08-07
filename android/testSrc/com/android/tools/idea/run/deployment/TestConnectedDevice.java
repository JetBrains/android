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

import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher;
import com.android.tools.idea.run.AndroidDevice;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TestConnectedDevice extends ConnectedDevice {
  @Nullable
  private final String myVirtualDeviceKey;

  @Nullable
  private final String myPhysicalDeviceName;

  static final class Builder extends ConnectedDevice.Builder {
    @Nullable
    private String myVirtualDeviceKey;

    @Nullable
    private String myPhysicalDeviceName;

    @NotNull
    @Override
    Builder setKey(@NotNull String key) {
      myKey = key;
      return this;
    }

    @NotNull
    @Override
    Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    Builder setVirtualDeviceKey(@NotNull @SuppressWarnings("SameParameterValue") String virtualDeviceKey) {
      myVirtualDeviceKey = virtualDeviceKey;
      return this;
    }

    @NotNull
    Builder setPhysicalDeviceName(@NotNull @SuppressWarnings("SameParameterValue") String physicalDeviceName) {
      myPhysicalDeviceName = physicalDeviceName;
      return this;
    }

    @NotNull
    @Override
    TestConnectedDevice build() {
      return new TestConnectedDevice(this);
    }
  }

  private TestConnectedDevice(@NotNull Builder builder) {
    super(builder);

    myVirtualDeviceKey = builder.myVirtualDeviceKey;
    myPhysicalDeviceName = builder.myPhysicalDeviceName;
  }

  @Nullable
  @Override
  String getVirtualDeviceKey() {
    return myVirtualDeviceKey;
  }

  @NotNull
  @Override
  String getPhysicalDeviceName(@NotNull DeviceNamePropertiesFetcher fetcher) {
    return myPhysicalDeviceName;
  }
}
