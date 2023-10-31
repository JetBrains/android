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

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConnectedDevice implements Device {
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

  private ConnectedDevice(@NotNull Builder builder) {
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
  static final class Builder extends Device.Builder {
    @NotNull
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    Builder setType(@NotNull Type type) {
      myType = type;
      return this;
    }

    @NotNull
    @SuppressWarnings("UnusedReturnValue")
    Builder setLaunchCompatibility(@NotNull LaunchCompatibility launchCompatibility) {
      myLaunchCompatibility = launchCompatibility;
      return this;
    }

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
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

  public boolean isVirtualDevice() {
    return myAndroidDevice.isVirtual();
  }

  public boolean isPhysicalDevice() {
    return !myAndroidDevice.isVirtual();
  }

  @NotNull
  @Override
  public Key key() {
    return myKey;
  }

  @NotNull
  @Override
  public Icon icon() {
    throw new UnsupportedOperationException();
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

  @Override
  public boolean connected() {
    throw new UnsupportedOperationException();
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
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Target> targets() {
    throw new UnsupportedOperationException();
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
    if (!(object instanceof ConnectedDevice device)) {
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
