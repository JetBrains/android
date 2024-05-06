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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Step to show a message that the SDK is missing.
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.MissingSdkAlertStep}
 */
public class MissingSdkAlertStep extends FirstRunWizardStep {
  public MissingSdkAlertStep() {
    super("Missing SDK");
  }

  @Override
  public void init() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JBLabel("<html><b>No Android SDK found.</b><br><br>Before continuing, you must download the necessary " +
                          "components or select an existing SDK."), BorderLayout.NORTH);
    setComponent(panel);
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }
}
