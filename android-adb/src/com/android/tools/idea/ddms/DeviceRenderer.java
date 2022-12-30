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
package com.android.tools.idea.ddms;

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_NAME;

import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import icons.StudioIcons;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JList;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceRenderer {

  // Prevent instantiation
  private DeviceRenderer() {
  }

  public static void renderDeviceName(@NotNull IDevice d,
                                      @NotNull DeviceNameProperties deviceProperties,
                                      @NotNull ColoredTextContainer component,
                                      boolean showSerialNumber) {
    renderDeviceName(d, deviceProperties, component, showSerialNumber, null);
  }

  public static void renderDeviceName(@NotNull IDevice d,
                                      @NotNull DeviceNameProperties deviceNameProperties,
                                      @NotNull ColoredTextContainer component,
                                      boolean showSerialNumber,
                                      @Nullable AvdManager avdManager) {
    component.setIcon(d.isEmulator() ? StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE : StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE);

    String name;
    if (d.isEmulator()) {
      name = getEmulatorDeviceName(d, avdManager);
    }
    else {
      name = getDeviceName(deviceNameProperties);
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
      component.append(DevicePropertyUtil.getBuild(deviceNameProperties.getBuildVersion(), null, deviceNameProperties.getApiLevel()),
                       SimpleTextAttributes.GRAY_ATTRIBUTES);
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
      avdName = d.getSerialNumber();
    }
    return String.format("%1$s %2$s ", "Emulator", avdName);
  }

  @NotNull
  private static String getDeviceName(@NotNull DeviceNameProperties deviceNameProperties) {
    String manufacturer = DevicePropertyUtil.getManufacturer(deviceNameProperties.getManufacturer(), false, "");
    String model = DevicePropertyUtil.getModel(deviceNameProperties.getModel(), "");
    if (model.toUpperCase(Locale.US).startsWith(manufacturer.toUpperCase(Locale.US))) {
      return String.format("%1$s ", model);
    } else {
      return String.format("%1$s %2$s ", manufacturer, model);
    }
  }

  public static boolean shouldShowSerialNumbers(@NotNull List<IDevice> devices, @NotNull DeviceNamePropertiesProvider provider) {
    Set<String> myNames = new HashSet<>();
    for (IDevice currentDevice : devices) {
      if (currentDevice.isEmulator()) {
        continue;
      }

      String currentName = getDeviceName(provider.get(currentDevice));
      if (myNames.contains(currentName)) {
        return true;
      }
      myNames.add(currentName);
    }
    return false;
  }

  static final class DeviceComboBoxRenderer extends ColoredListCellRenderer<IDevice> {
    @NotNull
    private String myEmptyText;
    private boolean myShowSerial;
    private DeviceNamePropertiesProvider myDeviceNamePropertiesProvider;

    DeviceComboBoxRenderer(@NotNull String emptyText,
                           boolean showSerial,
                           @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
      myEmptyText = emptyText;
      myShowSerial = showSerial;
      myDeviceNamePropertiesProvider = deviceNamePropertiesProvider;
    }

    public void setShowSerial(boolean showSerial) {
      myShowSerial = showSerial;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends IDevice> list,
                                         IDevice value,
                                         int index,
                                         boolean selected,
                                         boolean focused) {
      if (value == null) {
        append(myEmptyText, SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      renderDeviceName(value, myDeviceNamePropertiesProvider.get(value), this, myShowSerial);
    }
  }

  public static class DeviceNameRenderer extends ColoredTableCellRenderer {
    private final DeviceNameRendererEx[] myRenderers = DeviceNameRendererEx.EP_NAME.getExtensions();
    private final AvdManager myAvdManager;
    private final DeviceNamePropertiesProvider myDeviceNamePropertiesProvider;

    public DeviceNameRenderer(@Nullable AvdManager avdManager, @NotNull DeviceNamePropertiesProvider deviceNamePropertiesProvider) {
      myAvdManager = avdManager;
      myDeviceNamePropertiesProvider = deviceNamePropertiesProvider;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
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
      renderDeviceName(device, myDeviceNamePropertiesProvider.get(device), this, false, myAvdManager);
    }
  }
}
