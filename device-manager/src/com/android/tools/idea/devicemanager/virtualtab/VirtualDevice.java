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
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.device.Resolution;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.StorageDevice;
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle;
import icons.StudioIcons;
import java.util.Collection;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDevice extends Device {
  private final @Nullable Icon myIcon;
  private final @NotNull String myCpuArchitecture;
  private final long mySizeOnDisk;
  private final @NotNull State myState;
  private final @NotNull AvdInfo myAvdInfo;

  static final class Builder extends Device.Builder {
    private @Nullable Icon myIcon;
    private @Nullable String myCpuArchitecture;
    private long mySizeOnDisk;
    private @NotNull State myState = State.STOPPED;
    private @Nullable AvdInfo myAvdInfo;

    @NotNull
    Builder setKey(@NotNull Key key) {
      myKey = key;
      return this;
    }

    @NotNull
    Builder setIcon(@Nullable Icon icon) {
      myIcon = icon;
      return this;
    }

    @NotNull
    Builder setType(@NotNull DeviceType type) {
      myType = type;
      return this;
    }

    @NotNull
    Builder setName(@NotNull String name) {
      myName = name;
      return this;
    }

    @NotNull
    Builder setTarget(@NotNull String target) {
      myTarget = target;
      return this;
    }

    @NotNull
    Builder setCpuArchitecture(@NotNull String cpuArchitecture) {
      myCpuArchitecture = cpuArchitecture;
      return this;
    }

    @NotNull
    Builder setAndroidVersion(@NotNull AndroidVersion androidVersion) {
      myAndroidVersion = androidVersion;
      return this;
    }

    @NotNull
    Builder setSizeOnDisk(long sizeOnDisk) {
      mySizeOnDisk = sizeOnDisk;
      return this;
    }

    @NotNull
    Builder setState(@NotNull State state) {
      myState = state;
      return this;
    }

    @NotNull
    Builder setResolution(@Nullable Resolution resolution) {
      myResolution = resolution;
      return this;
    }

    @NotNull
    Builder setDensity(int density) {
      myDensity = density;
      return this;
    }

    @NotNull
    Builder addAllAbis(@NotNull Collection<String> abis) {
      myAbis.addAll(abis);
      return this;
    }

    @NotNull
    Builder setStorageDevice(@Nullable StorageDevice storageDevice) {
      myStorageDevice = storageDevice;
      return this;
    }

    @NotNull
    Builder setAvdInfo(@NotNull AvdInfo avdInfo) {
      myAvdInfo = avdInfo;
      return this;
    }

    @Override
    protected @NotNull VirtualDevice build() {
      return new VirtualDevice(this);
    }
  }

  enum State {
    STOPPED(false, StudioIcons.Avd.RUN, "Launch this AVD in the emulator") {
      @Override
      @SuppressWarnings("unused")
      boolean isEnabled(@NotNull VirtualDevice device) {
        return device.myAvdInfo.getStatus().equals(AvdStatus.OK);
      }
    },

    LAUNCHING(false, StudioIcons.Avd.RUN, "Launch this AVD in the emulator") {
      @Override
      @SuppressWarnings("unused")
      boolean isEnabled(@NotNull VirtualDevice device) {
        return false;
      }
    },

    LAUNCHED(true, StudioIcons.Avd.STOP, "Stop the emulator running this AVD") {
      @Override
      @SuppressWarnings("unused")
      boolean isEnabled(@NotNull VirtualDevice device) {
        return true;
      }
    },

    STOPPING(true, StudioIcons.Avd.STOP, "Stop the emulator running this AVD") {
      @Override
      @SuppressWarnings("unused")
      boolean isEnabled(@NotNull VirtualDevice device) {
        return false;
      }
    };

    private final boolean myOnline;
    private final @NotNull Icon myIcon;
    private final @NotNull String myTooltipText;

    State(boolean online, @NotNull Icon icon, @NotNull String tooltipText) {
      myOnline = online;
      myIcon = icon;
      myTooltipText = tooltipText;
    }

    static @NotNull State valueOf(boolean online) {
      return online ? LAUNCHED : STOPPED;
    }

    final @NotNull Icon getIcon() {
      return myIcon;
    }

    abstract boolean isEnabled(@NotNull VirtualDevice device);

    final @NotNull String getTooltipText() {
      return myTooltipText;
    }
  }

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
    myIcon = builder.myIcon;

    assert builder.myCpuArchitecture != null;
    myCpuArchitecture = builder.myCpuArchitecture;

    mySizeOnDisk = builder.mySizeOnDisk;
    myState = builder.myState;

    assert builder.myAvdInfo != null;
    myAvdInfo = builder.myAvdInfo;
  }

  @NotNull
  VirtualDevice withState(@NotNull State state) {
    return new VirtualDevice.Builder()
      .setKey(myKey)
      .setIcon(myIcon)
      .setType(myType)
      .setName(myName)
      .setTarget(myTarget)
      .setCpuArchitecture(myCpuArchitecture)
      .setAndroidVersion(myAndroidVersion)
      .setSizeOnDisk(mySizeOnDisk)
      .setState(state)
      .setResolution(myResolution)
      .setDensity(myDensity)
      .addAllAbis(myAbis)
      .setStorageDevice(myStorageDevice)
      .setAvdInfo(myAvdInfo)
      .build();
  }

  @Override
  public @NotNull Icon getIcon() {
    if (myIcon == null) {
      return myType.getVirtualIcon();
    }

    return myIcon;
  }

  @Override
  public boolean isOnline() {
    return myState.myOnline;
  }

  @NotNull
  String getCpuArchitecture() {
    return myCpuArchitecture;
  }

  long getSizeOnDisk() {
    return mySizeOnDisk;
  }

  @NotNull
  State getState() {
    return myState;
  }

  boolean isPairable() {
    return newPairingState().myPairable;
  }

  @Nullable
  String getPairingMessage() {
    return newPairingState().myMessage;
  }

  private @NotNull PairingState newPairingState() {
    return switch (myType) {
      case PHONE -> {
        if (myAndroidVersion.getApiLevel() < 30) {
          yield new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.api", 30));
        }

        if (!myAvdInfo.hasPlayStore()) {
          yield new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.play"));
        }

        yield new PairingState(true, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.ok"));
      }
      case TV, AUTOMOTIVE -> new PairingState(false, null);
      case WEAR_OS -> {
        if (myAndroidVersion.getApiLevel() < 28) {
          yield new PairingState(false, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.requires.api", 28));
        }

        yield new PairingState(true, AndroidWearPairingBundle.message("wear.assistant.device.list.tooltip.ok"));
      }
    };
  }

  public @NotNull AvdInfo getAvdInfo() {
    return myAvdInfo;
  }

  @Override
  public int hashCode() {
    int hashCode = myKey.hashCode();

    hashCode = 31 * hashCode + Objects.hashCode(myIcon);
    hashCode = 31 * hashCode + myType.hashCode();
    hashCode = 31 * hashCode + myName.hashCode();
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
    if (!(object instanceof VirtualDevice device)) {
      return false;
    }

    return myKey.equals(device.myKey) &&
           Objects.equals(myIcon, device.myIcon) &&
           myType.equals(device.myType) &&
           myName.equals(device.myName) &&
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
