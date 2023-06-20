/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.android.sdklib.AndroidVersion;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceManagerPairingDevice extends Device {
  private final @NotNull Icon myIcon;
  private final boolean myOnline;
  private final @NotNull Status myStatus;

  static final class Builder extends Device.Builder {
    private @Nullable Icon myIcon;
    private boolean myOnline;
    private @Nullable Status myStatus;

    @NotNull Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull Builder setType(@NotNull DeviceType type) {
      myType = type;
      return this;
    }

    @NotNull Builder setIcon(@NotNull Icon icon) {
      myIcon = icon;
      return this;
    }

    @NotNull Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull Builder setOnline(boolean online) {
      myOnline = online;
      return this;
    }

    @NotNull Builder setTarget(@NotNull String target) {
      myTarget = target;
      return this;
    }

    @NotNull Builder setStatus(@NotNull Status status) {
      myStatus = status;
      return this;
    }

    @NotNull Builder setAndroidVersion(@NotNull AndroidVersion androidVersion) {
      myAndroidVersion = androidVersion;
      return this;
    }

    @Override
    protected @NotNull DeviceManagerPairingDevice build() {
      return new DeviceManagerPairingDevice(this);
    }
  }

  private DeviceManagerPairingDevice(@NotNull Builder builder) {
    super(builder);

    assert builder.myIcon != null;
    myIcon = builder.myIcon;

    myOnline = builder.myOnline;

    assert builder.myStatus != null;
    myStatus = builder.myStatus;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean isOnline() {
    return myOnline;
  }

  @NotNull Status getStatus() {
    return myStatus;
  }

  @Override
  public int hashCode() {
    int hashCode = myKey.hashCode();

    hashCode = 31 * hashCode + myType.hashCode();
    hashCode = 31 * hashCode + myIcon.hashCode();
    hashCode = 31 * hashCode + myName.hashCode();
    hashCode = 31 * hashCode + Boolean.hashCode(myOnline);
    hashCode = 31 * hashCode + myTarget.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myStatus);
    hashCode = 31 * hashCode + myAndroidVersion.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myResolution);
    hashCode = 31 * hashCode + myDensity;
    hashCode = 31 * hashCode + myAbis.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myStorageDevice);

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof DeviceManagerPairingDevice)) {
      return false;
    }

    DeviceManagerPairingDevice device = (DeviceManagerPairingDevice)object;

    return myKey.equals(device.myKey) &&
           myType.equals(device.myType) &&
           myIcon.equals(device.myIcon) &&
           myName.equals(device.myName) &&
           myOnline == device.myOnline &&
           myTarget.equals(device.myTarget) &&
           Objects.equals(myStatus, device.myStatus) &&
           myAndroidVersion.equals(device.myAndroidVersion) &&
           Objects.equals(myResolution, device.myResolution) &&
           myDensity == device.myDensity &&
           myAbis.equals(device.myAbis) &&
           Objects.equals(myStorageDevice, device.myStorageDevice);
  }
}
