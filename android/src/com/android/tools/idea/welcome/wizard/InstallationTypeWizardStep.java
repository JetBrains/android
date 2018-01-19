/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.welcome.wizard.ConfigureInstallationModel.InstallationType;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.welcome.wizard.ConfigureInstallationModel.InstallationType.STANDARD;

/**
 * Wizard step for selecting installation types
 */
public class InstallationTypeWizardStep extends ModelWizardStep<ConfigureInstallationModel> {
  private JRadioButton myStandardRadioBtn;
  private JRadioButton myCustomRadioBtn;
  private JPanel myRootPanel;
  private JBScrollPane myRoot;

  private final BindingsManager myBindings = new BindingsManager();

  public InstallationTypeWizardStep(ConfigureInstallationModel model) {
    super(model, "Install Type");
    myRoot = StudioWizardStepPanel.wrappedWithVScroll(myRootPanel);

  }

  @Override
  protected void onEntering() {
    myBindings.bindTwoWay(
      new SelectedRadioButtonProperty<>(STANDARD, InstallationType.values(), myStandardRadioBtn, myCustomRadioBtn),
      getModel().installationType()
    );
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myStandardRadioBtn;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRoot;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }
}
