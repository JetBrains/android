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

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.google.common.collect.Sets;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_NAME;

public class DeviceRenderer {

  // Prevent instantiation
  private DeviceRenderer() {
  }

  public static void renderDeviceName(@NotNull IDevice d, @NotNull ColoredTextContainer component, boolean showSerialNumber) {
    renderDeviceName(d, component, showSerialNumber, null);
  }

  public static void renderDeviceName(@NotNull IDevice d,
                                      @NotNull ColoredTextContainer component,
                                      boolean showSerialNumber,
                                      @Nullable AvdManager avdManager) {
    component.setIcon(d.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);

    String name;
    if (d.isEmulator()) {
      name = getEmulatorDeviceName(d, avdManager);
    }
    else {
      name = getDeviceName(d);
    }

    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    IDevice.DeviceState deviceState = d.getState();
    if (deviceState != IDevice.DeviceState.ONLINE) {
      String state = String.format("%1$s [%2$s] ", d.getSerialNumber(), d.getState());
      component.append(state, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
    else if (showSerialNumber) {
      String state = String.format("%1$s ", d.getSerialNumber());
      component.append(state, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }

    if (deviceState != IDevice.DeviceState.DISCONNECTED && deviceState != IDevice.DeviceState.OFFLINE) {
      component.append(DevicePropertyUtil.getBuild(d), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @NotNull
  private static String getEmulatorDeviceName(@NotNull IDevice d, @Nullable AvdManager avdManager) {
    String avdName = d.getAvdName();
    if (avdManager != null) {
      AvdInfo info = avdManager.getAvd(avdName, true);
      if (info != null) {
        avdName = info.getProperties().get(AVD_INI_DISPLAY_NAME);
      }
    }
    if (avdName == null) {
      avdName = "unknown";
    }
    return String.format("%1$s %2$s ", AndroidBundle.message("android.emulator"), avdName);
  }

  @NotNull
  private static String getDeviceName(@NotNull IDevice d) {
    return String.format("%1$s %2$s ", DevicePropertyUtil.getManufacturer(d, ""), DevicePropertyUtil.getModel(d, ""));
  }

  public static boolean shouldShowSerialNumbers(@NotNull List<IDevice> devices) {
    Set<String> myNames = Sets.newHashSet();
    for (IDevice currentDevice : devices) {
      if (currentDevice.isEmulator()) {
        continue;
      }

      String currentName = getDeviceName(currentDevice);
      if (myNames.contains(currentName)) {
        return true;
      }
      myNames.add(currentName);
    }
    return false;
  }

  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer {

    @NotNull
    private String myEmptyText;
    private boolean myShowSerial;

    public DeviceComboBoxRenderer(@NotNull String emptyText, boolean showSerial) {
      myEmptyText = emptyText;
      myShowSerial = showSerial;
    }

    public void setShowSerial(boolean showSerial) {
      myShowSerial = showSerial;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof IDevice) {
        renderDeviceName((IDevice)value, this, myShowSerial);
      }
      else if (value == null) {
        append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }

  public static class DeviceNameRenderer extends ColoredTableCellRenderer {
    private static final ExtensionPointName<DeviceNameRendererEx> EP_NAME = ExtensionPointName.create("com.android.run.deviceNameRenderer");
    private final DeviceNameRendererEx[] myRenderers = EP_NAME.getExtensions();
    private final AvdManager myAvdManager;

    public DeviceNameRenderer(@Nullable AvdManager avdManager) {
      myAvdManager = avdManager;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (!(value instanceof IDevice)) {
        return;
      }

      IDevice device = (IDevice)value;
      for (DeviceNameRendererEx renderer : myRenderers) {
        if (renderer.isApplicable(device)) {
          renderer.render(device, this);
          return;
        }
      }

      renderDeviceName(device, this, false, myAvdManager);
    }
  }
}
