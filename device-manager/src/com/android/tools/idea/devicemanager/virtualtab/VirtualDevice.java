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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDevice extends Device {
  private final @NotNull String myCpuArchitecture;
  private final long mySizeOnDisk;
  private final @NotNull AvdInfo myAvdInfo;

  static final class Builder extends Device.Builder {
    private @Nullable String myCpuArchitecture;
    private long mySizeOnDisk;
    private @Nullable AvdInfo myAvdInfo;

    @NotNull Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull Builder setCpuArchitecture(@NotNull String cpuArchitecture) {
      myCpuArchitecture = cpuArchitecture;
      return this;
    }

    @NotNull Builder setType(@NotNull DeviceType type) {
      myType = type;
      return this;
    }

    @NotNull Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull Builder setTarget(@NotNull String target) {
      myTarget = target;
      return this;
    }

    @NotNull Builder setApi(@NotNull String api) {
      myApi = api;
      return this;
    }

    @NotNull Builder setSizeOnDisk(long sizeOnDisk) {
      mySizeOnDisk = sizeOnDisk;
      return this;
    }

    @NotNull Builder setResolution(@Nullable Resolution resolution) {
      myResolution = resolution;
      return this;
    }

    @NotNull Builder setDensity(int density) {
      myDensity = density;
      return this;
    }

    @NotNull Builder setAvdInfo(@NotNull AvdInfo avdInfo) {
      myAvdInfo = avdInfo;
      return this;
    }

    @Override
    protected @NotNull VirtualDevice build() {
      return new VirtualDevice(this);
    }
  }

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);

    assert builder.myCpuArchitecture != null;
    myCpuArchitecture = builder.myCpuArchitecture;

    mySizeOnDisk = builder.mySizeOnDisk;

    assert builder.myAvdInfo != null;
    myAvdInfo = builder.myAvdInfo;
  }

  @NotNull String getCpuArchitecture() {
    return myCpuArchitecture;
  }

  @Override
  protected @NotNull Icon getIcon() {
    return myType.getVirtualIcon();
  }

  @Override
  public boolean isOnline() {
    // TODO online should be a boolean property. Notify the Virtual tab of devices that come online in a way similar to
    //  PhysicalDeviceChangeListener.
    return AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(myAvdInfo);
  }

  long getSizeOnDisk() {
    return mySizeOnDisk;
  }

  public @NotNull AvdInfo getAvdInfo() {
    return myAvdInfo;
  }
}
