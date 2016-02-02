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

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AndroidDeviceRenderer extends ColoredListCellRenderer<DevicePickerEntry> {
  private final LaunchCompatibilityChecker myCompatibilityChecker;
  private final SpeedSearchBase mySpeedSearch;

  public AndroidDeviceRenderer(@NotNull LaunchCompatibilityChecker checker, @NotNull SpeedSearchBase speedSearch) {
    myCompatibilityChecker = checker;
    mySpeedSearch = speedSearch;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    if (value instanceof DevicePickerEntry && ((DevicePickerEntry)value).isMarker()) {
      String marker = ((DevicePickerEntry)value).getMarker();
      assert marker != null : "device picker marker entry doesn't have a descriptive string";

      if (value == DevicePickerEntry.NONE) {
        return renderEmptyMarker(marker);
      }
      else {
        return renderTitledSeparator(marker);
      }
    }

    return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
  }

  @Override
  protected void customizeCellRenderer(JList list, DevicePickerEntry entry, int index, boolean selected, boolean hasFocus) {
    AndroidDevice device = entry.getAndroidDevice();
    assert device != null;

    LaunchCompatibility launchCompatibility = myCompatibilityChecker.validate(device);
    boolean compatible = launchCompatibility.isCompatible() != ThreeState.NO;

    if (shouldShowSerialNumbers(list, device)) {
      append("[" + device.getSerial() + "] ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    device.renderName(this, compatible, mySpeedSearch.getEnteredPrefix());

    if (launchCompatibility.getReason() != null) {
      append(" (" + launchCompatibility.getReason() + ")", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
    }
  }

  private static boolean shouldShowSerialNumbers(@NotNull JList list, @NotNull AndroidDevice device) {
    if (device.isVirtual()) {
      return false;
    }

    ListModel model = list.getModel();
    if (model instanceof DevicePickerListModel) {
      return ((DevicePickerListModel)model).shouldShowSerialNumbers();
    }
    else {
      return false;
    }
  }

  private static Component renderTitledSeparator(@NotNull String title) {
    TitledSeparator separator = new TitledSeparator(title);
    separator.setBackground(UIUtil.getListBackground());
    separator.setTitleFont(UIUtil.getLabelFont());
    return separator;
  }

  private static Component renderEmptyMarker(@NotNull String title) {
    return new JLabel(title);
  }
}
