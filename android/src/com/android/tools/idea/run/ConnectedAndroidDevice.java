/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.ddms.DevicePropertyUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ConnectedAndroidDevice implements AndroidDevice {
  @NotNull private final IDevice myDevice;
  @Nullable private final String myAvdName;

  public ConnectedAndroidDevice(@NotNull IDevice device, @Nullable List<AvdInfo> avdInfos) {
    myDevice = device;

    AvdInfo avdInfo = getAvdInfo(device, avdInfos);
    myAvdName = avdInfo == null ? null : AvdManagerConnection.getAvdDisplayName(avdInfo);
  }

  @Nullable
  private static AvdInfo getAvdInfo(@NotNull IDevice device, @Nullable List<AvdInfo> avdInfos) {
    if (avdInfos != null && device.isEmulator()) {
      for (AvdInfo info : avdInfos) {
        if (info.getName().equals(device.getAvdName())) {
          return info;
        }
      }
    }

    return null;
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  @Override
  public boolean isVirtual() {
    return myDevice.isEmulator();
  }

  @NotNull
  @Override
  public AndroidVersion getVersion() {
    return DevicePropertyUtil.getDeviceVersion(myDevice);
  }

  @NotNull
  @Override
  public String getSerial() {
    return myDevice.getSerialNumber();
  }

  @Override
  public boolean supportsFeature(@NonNull IDevice.HardwareFeature feature) {
    return myDevice.supportsFeature(feature);
  }

  @Override
  public void renderName(@NotNull ColoredTextContainer renderer) {
    renderer.setIcon(myDevice.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);

    IDevice.DeviceState state = myDevice.getState();
    if (state != IDevice.DeviceState.ONLINE) {
      String name = String.format("%1$s [%2$s] ", myDevice.getSerialNumber(), myDevice.getState());
      renderer.append(name, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
      return;
    }

    String name = myAvdName == null ? getDeviceName() : myAvdName;
    renderer.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    String build = DevicePropertyUtil.getBuild(myDevice);
    if (!build.isEmpty()) {
      renderer.append(" (" + build + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private String getDeviceName() {
    StringBuilder name = new StringBuilder(20);
    name.append(DevicePropertyUtil.getManufacturer(myDevice, ""));
    if (name.length() > 0) {
      name.append(' ');
    }
    name.append(DevicePropertyUtil.getModel(myDevice, ""));
    return name.toString();
  }

  @NotNull
  public IDevice getDevice() {
    return myDevice;
  }
}
