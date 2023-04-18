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
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ConnectedDevice extends Device {
  private ConnectedDevice(@NotNull Builder builder) {
    super(builder);
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

  @NotNull
  @Override
  Collection<Snapshot> getSnapshots() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  Target getDefaultTarget() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  Collection<Target> getTargets() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getKey(), getType(), getLaunchCompatibility(), getConnectionTime(), getName(), getAndroidDevice());
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof ConnectedDevice device)) {
      return false;
    }

    return getKey().equals(device.getKey()) &&
           getType().equals(device.getType()) &&
           getLaunchCompatibility().equals(device.getLaunchCompatibility()) &&
           Objects.equals(getConnectionTime(), device.getConnectionTime()) &&
           getName().equals(device.getName()) &&
           getAndroidDevice().equals(device.getAndroidDevice());
  }
}
