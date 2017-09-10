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
package com.android.tools.idea.logcat.output;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LogcatOutputConfigurableUi implements ConfigurableUi<LogcatOutputSettings> {
  private JPanel myPanel;
  private JCheckBox myDebugOutputEnabledCheckBox;
  private JCheckBox myRunOutputEnabledCheckBox;

  @Override
  public boolean isModified(@NotNull LogcatOutputSettings settings) {
    return myRunOutputEnabledCheckBox.isSelected() != settings.isRunOutputEnabled() ||
           myDebugOutputEnabledCheckBox.isSelected() != settings.isDebugOutputEnabled();
  }

  @Override
  public void reset(@NotNull LogcatOutputSettings settings) {
    myRunOutputEnabledCheckBox.setSelected(settings.isRunOutputEnabled());
    myDebugOutputEnabledCheckBox.setSelected(settings.isDebugOutputEnabled());
  }

  @Override
  public void apply(@NotNull LogcatOutputSettings settings) throws ConfigurationException {
    settings.setRunOutputEnabled(myRunOutputEnabledCheckBox.isSelected());
    settings.setDebugOutputEnabled(myDebugOutputEnabledCheckBox.isSelected());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  public static boolean shouldShow() {
    return StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.get();
  }
}
