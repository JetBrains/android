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

import com.intellij.ui.components.JBScrollPane;
import javax.swing.JRadioButton;

/**
 * Wizard step UI for selecting installation types
 */
public class InstallationTypeWizardStepForm {
  private JBScrollPane myContents;
  private JRadioButton myStandardRadioButton;
  private JRadioButton myCustomRadioButton;

  public JBScrollPane getContents() {
    return myContents;
  }

  public JRadioButton getStandardRadioButton() {
    return myStandardRadioButton;
  }

  public JRadioButton getCustomRadioButton() {
    return myCustomRadioButton;
  }
}
