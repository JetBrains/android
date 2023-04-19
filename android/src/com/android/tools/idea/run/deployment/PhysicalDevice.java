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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import icons.StudioIcons;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record PhysicalDevice(@NotNull Key key,
                      @NotNull Type type,
                      @NotNull LaunchCompatibility launchCompatibility,
                      @Nullable Instant connectionTime,
                      @NotNull String name,
                      @NotNull AndroidDevice androidDevice) implements Device {
  private static final Icon ourPhoneIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE);
  private static final Icon ourWearIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR);
  private static final Icon ourTvIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV);

  private PhysicalDevice(@NotNull Builder builder) {
    this(Objects.requireNonNull(builder.myKey),
         builder.myType,
         builder.myLaunchCompatibility,
         builder.myConnectionTime,
         Objects.requireNonNull(builder.myName),
         Objects.requireNonNull(builder.myAndroidDevice));
  }

  static @NotNull PhysicalDevice newDevice(@NotNull Device device, @NotNull KeyToConnectionTimeMap map) {
    var key = device.key();

    return new Builder()
      .setKey(key)
      .setType(device.type())
      .setLaunchCompatibility(device.launchCompatibility())
      .setConnectionTime(map.get(key))
      .setName(device.name())
      .setAndroidDevice(device.androidDevice())
      .build();
  }

  @VisibleForTesting
  static final class Builder extends Device.Builder {
    @NotNull
    @VisibleForTesting
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setType(@NotNull Type type) {
      myType = type;
      return this;
    }

    @NotNull
    @VisibleForTesting
    Builder setLaunchCompatibility(@NotNull LaunchCompatibility launchCompatibility) {
      myLaunchCompatibility = launchCompatibility;
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
    Builder setName(@NotNull String name) {
      myName = name;
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

  @NotNull
  @Override
  public Icon icon() {
    var icon = switch (type) {
      case TV -> ourTvIcon;
      case WEAR -> ourWearIcon;
      case PHONE -> ourPhoneIcon;
    };

    return switch (launchCompatibility.getState()) {
      case ERROR -> new LayeredIcon(icon, StudioIcons.Common.ERROR_DECORATOR);
      case WARNING -> new LayeredIcon(icon, AllIcons.General.WarningDecorator);
      case OK -> icon;
    };
  }

  /**
   * @return true. Physical devices come and go as they are connected and disconnected; there are no instances of this class for
   * disconnected physical devices.
   */
  @Override
  public boolean connected() {
    return true;
  }

  @NotNull
  @Override
  public Collection<Snapshot> snapshots() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Target defaultTarget() {
    return new RunningDeviceTarget(key);
  }

  @NotNull
  @Override
  public Collection<Target> targets() {
    return List.of(new RunningDeviceTarget(key));
  }

  @NotNull
  @Override
  public String toString() {
    return name;
  }
}
