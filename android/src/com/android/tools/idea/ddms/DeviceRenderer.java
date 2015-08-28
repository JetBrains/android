/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ddms;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.CloudConfigurationProvider;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DeviceRenderer {

  // Prevent instantiation
  private DeviceRenderer() {
  }

  @VisibleForTesting
  static void renderDeviceName(IDevice d, ColoredTextContainer component) {
    renderDeviceName(d, component, null);
  }

  static void renderDeviceName(IDevice d, ColoredTextContainer component, AvdManager avdManager) {
    component.setIcon(d.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);

    String name;
    if (d.isEmulator()) {
      String avdName = d.getAvdName();
      if (avdManager != null) {
        AvdInfo info = avdManager.getAvd(avdName, true);
        if (info != null) {
          avdName = info.getProperties().get(AvdManagerConnection.AVD_INI_DISPLAY_NAME);
        }
      }
      if (avdName == null) {
        avdName = "unknown";
      }
      name = String.format("%1$s %2$s ", AndroidBundle.message("android.emulator"), avdName);
    }
    else {
      name = String.format("%1$s %2$s ", DevicePropertyUtil.getManufacturer(d, ""), DevicePropertyUtil.getModel(d, ""));
    }

    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    IDevice.DeviceState deviceState = d.getState();
    if (deviceState != IDevice.DeviceState.ONLINE) {
      String state = String.format("%1$s [%2$s] ", d.getSerialNumber(), d.getState());
      component.append(state, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }

    if (deviceState != IDevice.DeviceState.DISCONNECTED && deviceState != IDevice.DeviceState.OFFLINE) {
      component.append(DevicePropertyUtil.getBuild(d), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  private static void renderCloudDeviceName(IDevice device, ColoredTextContainer component,
                                            @NotNull CloudConfigurationProvider cloudConfigurationProvider) {
    component.setIcon(cloudConfigurationProvider.getCloudDeviceIcon());
    String cloudDeviceConfiguration = cloudConfigurationProvider.getCloudDeviceConfiguration(device);
    if (device.getState() == IDevice.DeviceState.OFFLINE) {
      component.append("Launching " + cloudDeviceConfiguration, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
    else {
      component.append(cloudDeviceConfiguration, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer {

    @NotNull
    private String myEmptyText;

    public DeviceComboBoxRenderer(@NotNull String emptyText) {
      myEmptyText = emptyText;
    }

    public DeviceComboBoxRenderer() {
      this("[none]");
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof IDevice) {
        renderDeviceName((IDevice)value, this);
      }
      else if (value == null) {
        append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }

  public static class DeviceNameRenderer extends ColoredTableCellRenderer {
    private final static CloudConfigurationProvider CLOUD_CONFIGURATION_PROVIDER =
      CloudConfigurationProvider.getCloudConfigurationProvider();

    private final AvdManager myAvdManager;
    public DeviceNameRenderer(@Nullable AvdManager avdManager) {
      myAvdManager = avdManager;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof IDevice) {
        IDevice device = (IDevice)value;
        if (CLOUD_CONFIGURATION_PROVIDER != null && CLOUD_CONFIGURATION_PROVIDER.getCloudDeviceConfiguration(device) != null) {
          // This is a cloud device, so use a specific rendering.
          renderCloudDeviceName(device, this, CLOUD_CONFIGURATION_PROVIDER);
        }
        else {
          renderDeviceName(device, this, myAvdManager);
        }
      }
    }
  }
}
