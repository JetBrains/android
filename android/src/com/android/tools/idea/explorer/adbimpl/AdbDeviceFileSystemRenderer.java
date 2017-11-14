/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider;
import com.android.tools.idea.ddms.DeviceRenderer;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class AdbDeviceFileSystemRenderer implements DeviceFileSystemRenderer {
  @NotNull private final DeviceNameRenderer myDeviceNameRenderer;

  public AdbDeviceFileSystemRenderer(@NotNull AdbDeviceFileSystemService service,
                                     @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
    myDeviceNameRenderer = new DeviceNameRenderer(service, deviceNamePropertiesProvider);
  }

  @NotNull
  @Override
  public ListCellRenderer<DeviceFileSystem> getDeviceNameListRenderer() {
    return myDeviceNameRenderer;
  }

  private static class DeviceNameRenderer implements ListCellRenderer<DeviceFileSystem> {
    @NotNull private final AdbDeviceFileSystemService myService;
    @NotNull private final DeviceRenderer.DeviceComboBoxRenderer myRendererImpl;

    public DeviceNameRenderer(@NotNull AdbDeviceFileSystemService service,
                              @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
      myService = service;
      myRendererImpl = new DeviceRenderer.DeviceComboBoxRenderer(
        "No Connected Devices", false, deviceNamePropertiesProvider);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends DeviceFileSystem> list,
                                                  DeviceFileSystem value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      List<IDevice> deviceList = myService.getDeviceList()
        .stream()
        .map(x -> ((AdbDeviceFileSystem)x).getDevice()).collect(Collectors.toList());
      myRendererImpl.setShowSerial(DeviceRenderer.shouldShowSerialNumbers(deviceList));

      IDevice device = value == null ? null : ((AdbDeviceFileSystem)value).getDevice();
      //noinspection unchecked
      return myRendererImpl.getListCellRendererComponent(list, device, index, isSelected, cellHasFocus);
    }
  }
}
