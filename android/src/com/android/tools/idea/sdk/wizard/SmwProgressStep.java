/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard;

import com.android.tools.idea.wizard.TemplateWizardStep;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SmwProgressStep extends TemplateWizardStep implements Disposable {
  private final SmwState myWizardState;
  private JPanel myContentPanel;
  private JTextArea myTextArea1;
  private JProgressBar myProgressBar;
  private JBLabel myLabelSdkPath;
  private JBLabel myLabelProgress1;
  private JBLabel myLabelProgress2;
  private JLabel myErrorLabel;

  public SmwProgressStep(@NotNull SmwState wizardState, @Nullable TemplateWizardStep.UpdateListener updateListener) {
    super(wizardState, null /*project*/, null /*module*/, null /*sidePanelIcon*/, updateListener);
    myWizardState = wizardState;
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public void _init() {
    super._init();
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    super._commit(finishChosen);
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myErrorLabel;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myErrorLabel;
  }
}
