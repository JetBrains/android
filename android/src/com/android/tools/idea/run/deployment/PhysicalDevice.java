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

final class PhysicalDevice implements Device {
  private static final Icon ourPhoneIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE);
  private static final Icon ourWearIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR);
  private static final Icon ourTvIcon = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV);

  @NotNull
  private final Key myKey;

  @NotNull
  private final Type myType;

  @NotNull
  private final LaunchCompatibility myLaunchCompatibility;

  @Nullable
  private final Instant myConnectionTime;

  @NotNull
  private final String myName;

  @NotNull
  private final AndroidDevice myAndroidDevice;

  private PhysicalDevice(@NotNull Builder builder) {
    assert builder.myKey != null;
    myKey = builder.myKey;

    myType = builder.myType;
    myLaunchCompatibility = builder.myLaunchCompatibility;
    myConnectionTime = builder.myConnectionTime;

    assert builder.myName != null;
    myName = builder.myName;

    assert builder.myAndroidDevice != null;
    myAndroidDevice = builder.myAndroidDevice;
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
  public Key key() {
    return myKey;
  }

  @NotNull
  @Override
  public Icon icon() {
    var icon = switch (myType) {
      case TV -> ourTvIcon;
      case WEAR -> ourWearIcon;
      case PHONE -> ourPhoneIcon;
    };

    return switch (myLaunchCompatibility.getState()) {
      case ERROR -> new LayeredIcon(icon, StudioIcons.Common.ERROR_DECORATOR);
      case WARNING -> new LayeredIcon(icon, AllIcons.General.WarningDecorator);
      case OK -> icon;
    };
  }

  @NotNull
  @Override
  public Type type() {
    return myType;
  }

  @NotNull
  @Override
  public LaunchCompatibility launchCompatibility() {
    return myLaunchCompatibility;
  }

  /**
   * @return true. Physical devices come and go as they are connected and disconnected; there are no instances of this class for
   * disconnected physical devices.
   */
  @Override
  public boolean connected() {
    return true;
  }

  @Nullable
  @Override
  public Instant connectionTime() {
    return myConnectionTime;
  }

  @NotNull
  @Override
  public String name() {
    return myName;
  }

  @NotNull
  @Override
  public Collection<Snapshot> snapshots() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Target defaultTarget() {
    return new RunningDeviceTarget(myKey);
  }

  @NotNull
  @Override
  public Collection<Target> targets() {
    return List.of(new RunningDeviceTarget(myKey));
  }

  @NotNull
  @Override
  public AndroidDevice androidDevice() {
    return myAndroidDevice;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myKey, myType, myLaunchCompatibility, myConnectionTime, myName, myAndroidDevice);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof PhysicalDevice device)) {
      return false;
    }

    return myKey.equals(device.myKey) &&
           myType.equals(device.myType) &&
           myLaunchCompatibility.equals(device.myLaunchCompatibility) &&
           Objects.equals(myConnectionTime, device.myConnectionTime) &&
           myName.equals(device.myName) &&
           myAndroidDevice.equals(device.myAndroidDevice);
  }

  @NotNull
  @Override
  public String toString() {
    return myName;
  }
}
