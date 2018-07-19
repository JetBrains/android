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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.intellij.execution.runners.ExecutionUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.Objects;

final class VirtualDevice extends Device {
  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.EmulatorDevice);
  private final boolean myConnected;

  @VisibleForTesting
  VirtualDevice(boolean connected, @NotNull String name) {
    super(name);
    myConnected = connected;
  }

  @NotNull
  static Device newVirtualDevice(@NotNull AvdInfo virtualDevice, @NotNull Iterable<IDevice> connectedDevices) {
    Object name = virtualDevice.getName();

    for (Iterator<IDevice> i = connectedDevices.iterator(); i.hasNext(); ) {
      if (Objects.equals(i.next().getAvdName(), name)) {
        i.remove();
        return new VirtualDevice(true, AvdManagerConnection.getAvdDisplayName(virtualDevice));
      }
    }

    return new VirtualDevice(false, AvdManagerConnection.getAvdDisplayName(virtualDevice));
  }

  @NotNull
  @Override
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : AndroidIcons.Ddms.EmulatorDevice;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;
    return myConnected == device.myConnected && myName.equals(device.myName);
  }

  @Override
  public int hashCode() {
    return 31 * Boolean.hashCode(myConnected) + myName.hashCode();
  }
}
