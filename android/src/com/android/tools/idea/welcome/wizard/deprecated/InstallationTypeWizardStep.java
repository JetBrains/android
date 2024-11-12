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

import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Wizard step for selecting installation types
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.InstallationTypeWizardStep}
 */
@Deprecated
public class InstallationTypeWizardStep extends FirstRunWizardStep {
  @NotNull private final ScopedStateStore.Key<Boolean> myDataKey;
  private final InstallationTypeWizardStepForm myForm = new InstallationTypeWizardStepForm();

  public InstallationTypeWizardStep(@NotNull ScopedStateStore.Key<Boolean> customInstall) {
    super("Install Type");
    myDataKey = customInstall;
    setComponent(myForm.getContents());
  }

  @Override
  public void init() {
    register(myDataKey, myForm.getContents(), new TwoRadiosToBooleanBinding(myForm.getCustomRadioButton(), myForm.getStandardRadioButton()));
  }

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myForm.getStandardRadioButton();
  }
}
