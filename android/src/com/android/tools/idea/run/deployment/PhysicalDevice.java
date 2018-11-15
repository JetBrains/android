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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import java.util.Collections;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice extends Device {
  private static final Icon ourIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.RealDevice);

  @NotNull
  static PhysicalDevice newPhysicalDevice(@NotNull DeviceNameProperties properties,
                                          @NotNull ConnectionTimeService service,
                                          @NotNull IDevice ddmlibDevice) {
    String key = ddmlibDevice.getSerialNumber();

    return new Builder()
      .setName(getName(properties))
      .setKey(key)
      .setConnectionTime(service.get(key))
      .setDdmlibDevice(ddmlibDevice)
      .build();
  }

  static final class Builder extends Device.Builder<Builder> {
    @NotNull
    @Override
    Builder self() {
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
  Icon getIcon() {
    return ourIcon;
  }

  /**
   * @return true. Physical devices come and go as they are connected and disconnected; there are no instances of this class for
   * disconnected physical devices.
   */
  @Override
  boolean isConnected() {
    return true;
  }

  @NotNull
  @Override
  ImmutableCollection<String> getSnapshots() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  DeviceFutures newDeviceFutures(@NotNull Project project, @Nullable String snapshot) {
    IDevice device = getDdmlibDevice();
    assert device != null;

    return new DeviceFutures(Collections.singletonList(new ConnectedAndroidDevice(device, null)));
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    Device device = (Device)object;

    return getName().equals(device.getName()) &&
           getKey().equals(device.getKey()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           Objects.equals(getDdmlibDevice(), device.getDdmlibDevice());
  }

  @Override
  public int hashCode() {
    int hashCode = getName().hashCode();

    hashCode = 31 * hashCode + getKey().hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(getConnectionTime());
    hashCode = 31 * hashCode + Objects.hashCode(getDdmlibDevice());

    return hashCode;
  }
}
