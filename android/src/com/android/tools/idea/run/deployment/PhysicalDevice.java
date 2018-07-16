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
import com.android.tools.idea.ddms.DeviceNameProperties;
import com.intellij.execution.runners.ExecutionUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class PhysicalDevice extends Device {
  private static final Icon ourIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.RealDevice);

  @VisibleForTesting
  PhysicalDevice(@NotNull String name) {
    super(name);
  }

  PhysicalDevice(@NotNull DeviceNameProperties properties) {
    super(getName(properties));
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

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice)) {
      return false;
    }

    return myName.equals(((PhysicalDevice)object).myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }
}
