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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;

public class DeviceRenderer {

  // Prevent instantiation
  private DeviceRenderer() {
  }

  @VisibleForTesting
  static void renderDeviceName(IDevice d, ColoredTextContainer component) {
    component.setIcon(d.isEmulator() ? AndroidIcons.Ddms.Emulator2 : AndroidIcons.Ddms.RealDevice);

    String name;
    if (d.isEmulator()) {
      String avdName = d.getAvdName();
      if (avdName == null) {
        avdName = "unknown";
      }
      name = String.format("%1$s %2$s ", AndroidBundle.message("android.emulator"), avdName);
    }
    else {
      name = String.format("%1$s %2$s ", DevicePropertyUtil.getManufacturer(d, ""), DevicePropertyUtil.getModel(d, ""));
    }

    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (d.getState() != IDevice.DeviceState.ONLINE) {
      String state = String.format("%1$s [%2$s] ", d.getSerialNumber(), d.getState());
      component.append(state, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }

    component.append(DevicePropertyUtil.getBuild(d), SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  public static class DeviceComboBoxRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof IDevice) {
        renderDeviceName((IDevice)value, this);
      }
      else if (value == null) {
        append("[none]", SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }

  public static class DeviceNameRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      if (value instanceof IDevice) {
        renderDeviceName((IDevice)value, this);
      }
    }
  }
}
