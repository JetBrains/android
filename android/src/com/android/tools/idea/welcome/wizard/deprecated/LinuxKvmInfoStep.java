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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.openapi.util.SystemInfo;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.Nullable;

/**
 * Provides guidance for setting up KVM on Linux platform.
 *
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.LinuxKvmInfoStep}
 */
@Deprecated
public class LinuxKvmInfoStep extends FirstRunWizardStep {
  private final LinuxKvmInfoStepForm myForm = new LinuxKvmInfoStepForm();

  public LinuxKvmInfoStep() {
    super("Emulator Settings");
    setComponent(myForm.getRoot());
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
    return myForm.getUrlPane();
  }

  @Override
  public boolean isStepVisible() {
    return SystemInfo.isLinux;
  }
}
