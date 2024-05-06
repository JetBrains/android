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
package com.android.tools.idea.run.deployment.legacyselector;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.LaunchCompatibility;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ConnectedDevice(@NotNull Key key,
                       @NotNull Icon icon,
                       @NotNull Type type,
                       @NotNull LaunchCompatibility launchCompatibility,
                       @Nullable Instant connectionTime,
                       @NotNull String name,
                       @NotNull AndroidDevice androidDevice) implements Device {
  private ConnectedDevice(@NotNull Builder builder) {
    this(Objects.requireNonNull(builder.myKey),
         Objects.requireNonNull(builder.myIcon),
         builder.myType,
         builder.myLaunchCompatibility,
         builder.myConnectionTime,
         Objects.requireNonNull(builder.myName),
         Objects.requireNonNull(builder.myAndroidDevice));
  }

  public static final class Builder extends Device.Builder {
    @Nullable
    private Icon myIcon;

    @NotNull
    public Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Builder setIcon(@NotNull Icon icon) {
      myIcon = icon;
      return this;
    }

    @NotNull
    public Builder setType(@NotNull Type type) {
      myType = type;
      return this;
    }

    @NotNull
    @SuppressWarnings("UnusedReturnValue")
    public Builder setLaunchCompatibility(@NotNull LaunchCompatibility launchCompatibility) {
      myLaunchCompatibility = launchCompatibility;
      return this;
    }

    @NotNull
    public Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    public Builder setAndroidDevice(@NotNull AndroidDevice androidDevice) {
      myAndroidDevice = androidDevice;
      return this;
    }

    @NotNull
    @Override
    public ConnectedDevice build() {
      return new ConnectedDevice(this);
    }
  }

  public boolean isVirtualDevice() {
    return androidDevice.isVirtual();
  }

  public boolean isPhysicalDevice() {
    return !androidDevice.isVirtual();
  }

  @Override
  public boolean connected() {
    throw new UnsupportedOperationException();
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
  public String toString() {
    return name;
  }
}
