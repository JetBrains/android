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
package com.android.tools.idea.welcome.wizard;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides guidance for setting up Intel® HAXM on Linux platform.
 */
public class LinuxHaxmInfoStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private com.intellij.ui.HyperlinkLabel myLink;

  private static final String KVM_DOCUMENTATION_URL = "http://developer.android.com/tools/devices/emulator.html#vm-linux";

  public LinuxHaxmInfoStep() {
    super("Emulator Settings");
    setComponent(myRoot);
    myLink.setHyperlinkText("Android KVM Linux Installation");
    myLink.setHyperlinkTarget(KVM_DOCUMENTATION_URL);
  }

  @Override
  public void init() {

  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLink;
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isLinux;
  }
}
