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

import com.android.tools.idea.devicemanager.physicaltab.Key;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceManagerPairingDevice extends Device {
  private final @NotNull Icon myIcon;
  private final boolean myOnline;

  static final class Builder extends Device.Builder {
    private @Nullable Icon myIcon;
    private boolean myOnline;

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

    @NotNull Builder setApi(@NotNull String api) {
      myApi = api;
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
  }

  @Override
  protected @NotNull Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean isOnline() {
    return myOnline;
  }
}
