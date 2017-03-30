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
package com.android.tools.idea.ddms.adb;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AdbConfigurableUi implements ConfigurableUi<AdbOptionsService> {
  private JPanel myPanel;
  private JCheckBox myUseLibusbCheckbox;

  @Override
  public boolean isModified(@NotNull AdbOptionsService settings) {
    return myUseLibusbCheckbox.isSelected() != settings.shouldUseLibusb();
  }

  @Override
  public void reset(@NotNull AdbOptionsService settings) {
    myUseLibusbCheckbox.setSelected(settings.shouldUseLibusb());
  }

  @Override
  public void apply(@NotNull AdbOptionsService settings) throws ConfigurationException {
    settings.setUseLibusb(myUseLibusbCheckbox.isSelected());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  public static boolean shouldShow() {
    // Currently, the libusb backend is only supported on Linux & Mac
    return SystemInfo.isMac || SystemInfo.isLinux;
  }
}
