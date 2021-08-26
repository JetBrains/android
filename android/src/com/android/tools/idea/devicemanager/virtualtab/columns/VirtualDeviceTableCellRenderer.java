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
package com.android.tools.idea.devicemanager.virtualtab.columns;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.util.Targets;
import java.awt.Component;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

public final class VirtualDeviceTableCellRenderer extends DeviceTableCellRenderer<VirtualDevice> {

  public VirtualDeviceTableCellRenderer() {
    super(VirtualDevice.class);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    AvdInfo avdInfo = (AvdInfo)value;
    IdDisplay tag = avdInfo.getTag();

    Object virtualDevice = new VirtualDevice.Builder()
      .setKey(new VirtualDeviceName(avdInfo.getName()))
      .setCpuArchitecture(avdInfo.getCpuArch())
      .setType(getType(tag))
      .setName(avdInfo.getDisplayName())
      .setOnline(AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(avdInfo))
      .setTarget(Targets.toString(avdInfo.getAndroidVersion(), tag))
      .build();

    return super.getTableCellRendererComponent(table, virtualDevice, selected, focused, viewRowIndex, viewColumnIndex);
  }

  private static @NotNull DeviceType getType(@NotNull IdDisplay tag) {
    if (tag.equals(SystemImage.WEAR_TAG)) {
      return DeviceType.WEAR_OS;
    }
    else if (tag.equals(SystemImage.ANDROID_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.GOOGLE_TV_TAG)) {
      return DeviceType.TV;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else if (tag.equals(SystemImage.AUTOMOTIVE_PLAY_STORE_TAG)) {
      return DeviceType.AUTOMOTIVE;
    }
    else {
      return DeviceType.PHONE;
    }
  }

  @Override
  protected @NotNull String getLine2(@NotNull VirtualDevice device) {
    return device.getTarget() + " | " + device.getCpuArchitecture();
  }
}
