/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This is to be shown as the first HAXM Wizard step just to inform the user that
 * HAXM uninstallation is about to start. It is here just to make sure we don't
 * run uninstallation operations straight away as the first wizard step, as this
 * would not be in line with common wizard conventions
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.HaxmUninstallInfoStep}
 */
public class HaxmUninstallInfoStep extends FirstRunWizardStep {
  private JPanel myRoot;

  public HaxmUninstallInfoStep() {
    super("Uninstalling HAXM");
    setComponent(myRoot);
  }

  @Override
  public void init() {}

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myRoot;
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isMac || SystemInfo.isWindows;
  }
}
