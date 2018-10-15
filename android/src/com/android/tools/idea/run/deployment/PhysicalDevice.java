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
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ConnectedAndroidDevice;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import icons.AndroidIcons;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PhysicalDevice extends Device {
  private static final Icon ourIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.RealDevice);

  PhysicalDevice(@NotNull DeviceNameProperties properties, @NotNull IDevice ddmlibDevice) {
    super(getName(properties), ddmlibDevice);
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

  @VisibleForTesting
  PhysicalDevice(@NotNull String name) {
    super(name, null);
  }

  @NotNull
  @Override
  Icon getIcon() {
    return ourIcon;
  }

  @NotNull
  @Override
  ImmutableCollection<String> getSnapshots() {
    return ImmutableList.of();
  }

  @NotNull
  @Override
  AndroidDevice toAndroidDevice() {
    IDevice device = getDdmlibDevice();
    assert device != null;

    return new ConnectedAndroidDevice(device, null);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    PhysicalDevice device = (PhysicalDevice)object;
    return getName().equals(device.getName()) && Objects.equals(getDdmlibDevice(), device.getDdmlibDevice());
  }

  @Override
  public int hashCode() {
    return 31 * getName().hashCode() + Objects.hashCode(getDdmlibDevice());
  }
}
