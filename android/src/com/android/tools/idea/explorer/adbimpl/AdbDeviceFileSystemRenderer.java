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
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.stream.Collectors;

public final class AdbDeviceFileSystemRenderer implements DeviceFileSystemRenderer<AdbDeviceFileSystem> {
  @NotNull private final DeviceNameRenderer myDeviceNameRenderer;

  public AdbDeviceFileSystemRenderer(@NotNull AdbDeviceFileSystemService service,
                                     @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
    myDeviceNameRenderer = new DeviceNameRenderer(service, deviceNamePropertiesProvider);
  }

  @NotNull
  @Override
  public ListCellRenderer<AdbDeviceFileSystem> getDeviceNameListRenderer() {
    return myDeviceNameRenderer;
  }

  private static final class DeviceNameRenderer extends ColoredListCellRenderer<AdbDeviceFileSystem> {
    @NotNull private final AdbDeviceFileSystemService myService;
    private final DeviceNamePropertiesProvider myDeviceNamePropertiesProvider;

    public DeviceNameRenderer(@NotNull AdbDeviceFileSystemService service,
                              @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
      myService = service;
      myDeviceNamePropertiesProvider = deviceNamePropertiesProvider;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends AdbDeviceFileSystem> list,
                                         AdbDeviceFileSystem value,
                                         int index,
                                         boolean selected,
                                         boolean focused) {
      if (value == null) {
        append("No Connected Devices", SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      IDevice device = value.getDevice();

      List<IDevice> devices = myService.getDeviceList().stream()
        .map(AdbDeviceFileSystem::getDevice)
        .collect(Collectors.toList());

      boolean showSerialNumbers = DeviceRenderer.shouldShowSerialNumbers(devices);
      DeviceRenderer.renderDeviceName(device, myDeviceNamePropertiesProvider.get(device), this, showSerialNumbers);
    }
  }
}
