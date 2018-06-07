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
package com.android.tools.idea.welcome.wizard;

import com.android.tools.idea.npw.platform.FormFactorUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Welcome page for the first run wizard
 */
public class FirstRunWelcomeStep extends ModelWizardStep.WithoutModel {
  private JBScrollPane myRoot;
  private JLabel myIcons;
  private JPanel myExistingSdkMessage;
  private JPanel myNewSdkMessage;

  public FirstRunWelcomeStep(boolean sdkExists) {
    super("Welcome");

    myIcons.setIcon(FormFactorUtils.getFormFactorsImage(myIcons, false));
    myExistingSdkMessage.setVisible(sdkExists);
    myNewSdkMessage.setVisible(!sdkExists);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRoot;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    // Doesn't matter
    return null;
  }
}
