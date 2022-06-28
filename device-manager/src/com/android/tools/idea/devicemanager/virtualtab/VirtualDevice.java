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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.StorageDevice;
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle;
import java.util.Collection;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDevice extends Device {
  private final boolean myOnline;
  private final @NotNull String myCpuArchitecture;
  private final long mySizeOnDisk;
  private final @NotNull LaunchOrStopButtonState myState;
  private final @NotNull AvdInfo myAvdInfo;

  static final class Builder extends Device.Builder {
    private boolean myOnline;
    private @Nullable String myCpuArchitecture;
    private long mySizeOnDisk;
    private @NotNull LaunchOrStopButtonState myState = LaunchOrStopButtonState.STOPPED;
    private @Nullable AvdInfo myAvdInfo;

    @NotNull Builder setKey(@NotNull Key key) {
      myKey = key;
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

    @NotNull Builder setOnline(boolean online) {
      myOnline = online;
      return this;
    }

    @NotNull Builder setTarget(@NotNull String target) {
      myTarget = target;
      return this;
    }

    @NotNull Builder setCpuArchitecture(@NotNull String cpuArchitecture) {
      myCpuArchitecture = cpuArchitecture;
      return this;
    }

    @NotNull Builder setAndroidVersion(@NotNull AndroidVersion androidVersion) {
      myAndroidVersion = androidVersion;
      return this;
    }

    @NotNull Builder setSizeOnDisk(long sizeOnDisk) {
      mySizeOnDisk = sizeOnDisk;
      return this;
    }

    @NotNull Builder setState(@NotNull LaunchOrStopButtonState state) {
      myState = state;
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

    @NotNull Builder addAllAbis(@NotNull Collection<@NotNull String> abis) {
      myAbis.addAll(abis);
      return this;
    }

    @NotNull Builder setStorageDevice(@Nullable StorageDevice storageDevice) {
      myStorageDevice = storageDevice;
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

  private enum LaunchOrStopButtonState {STOPPED, LAUNCHING, LAUNCHED, STOPPING}

  private static final class PairingState {
    private final boolean myPairable;
    private final @Nullable String myMessage;

    private PairingState(boolean pairable, @Nullable String message) {
      myPairable = pairable;
      myMessage = message;
    }
  }

  private VirtualDevice(@NotNull Builder builder) {
    super(builder);
    myOnline = builder.myOnline;

    assert builder.myCpuArchitecture != null;
    myCpuArchitecture = builder.myCpuArchitecture;

    mySizeOnDisk = builder.mySizeOnDisk;
    myState = builder.myState;

    assert builder.myAvdInfo != null;
    myAvdInfo = builder.myAvdInfo;
  }

  @Override
  protected @NotNull Icon getIcon() {
    return myType.getVirtualIcon();
  }

  @Override
  public boolean isOnline() {
    return myOnline;
  }

  @NotNull String getCpuArchitecture() {
    return myCpuArchitecture;
  }

  long getSizeOnDisk() {
    return mySizeOnDisk;
  }

  boolean isPairable() {
    return newPairingState().myPairable;
  }

  @Nullable String getPairingMessage() {
    return newPairingState().myMessage;
  }

  private @NotNull PairingState newPairingState() {
    switch (myType) {
      case PHONE:
        if (myAndroidVersion.getApiLevel() < 30) {
          return new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.api", 30));
        }

        if (!myAvdInfo.hasPlayStore()) {
          return new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.play"));
        }

        return new PairingState(true, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.ok"));
      case WEAR_OS:
        if (myAndroidVersion.getApiLevel() < 28) {
          return new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.api", 28));
        }

        return new PairingState(true, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.ok"));
      default:
        return new PairingState(false, null);
    }
  }

  public @NotNull AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @Override
  public int hashCode() {
    int hashCode = myKey.hashCode();

    hashCode = 31 * hashCode + myType.hashCode();
    hashCode = 31 * hashCode + myName.hashCode();
    hashCode = 31 * hashCode + Boolean.hashCode(myOnline);
    hashCode = 31 * hashCode + myTarget.hashCode();
    hashCode = 31 * hashCode + myCpuArchitecture.hashCode();
    hashCode = 31 * hashCode + myAndroidVersion.hashCode();
    hashCode = 31 * hashCode + Long.hashCode(mySizeOnDisk);
    hashCode = 31 * hashCode + myState.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myResolution);
    hashCode = 31 * hashCode + myDensity;
    hashCode = 31 * hashCode + myAbis.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(myStorageDevice);
    hashCode = 31 * hashCode + myAvdInfo.hashCode();

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;

    return myKey.equals(device.myKey) &&
           myType.equals(device.myType) &&
           myName.equals(device.myName) &&
           myOnline == device.myOnline &&
           myTarget.equals(device.myTarget) &&
           myCpuArchitecture.equals(device.myCpuArchitecture) &&
           myAndroidVersion.equals(device.myAndroidVersion) &&
           mySizeOnDisk == device.mySizeOnDisk &&
           myState.equals(device.myState) &&
           Objects.equals(myResolution, device.myResolution) &&
           myDensity == device.myDensity &&
           myAbis.equals(device.myAbis) &&
           Objects.equals(myStorageDevice, device.myStorageDevice) &&
           myAvdInfo.equals(device.myAvdInfo);
  }
}
