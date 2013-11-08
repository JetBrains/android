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

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;

public class SmwProgressStep extends SmwStep {
  private final SmwState myWizardState;
  private JPanel myContentPanel;
  private JTextArea myTextArea1;
  private JProgressBar myProgressBar;
  private JBLabel myLabelSdkPath;
  private JBLabel myLabelProgress1;
  private JBLabel myLabelProgress2;

  public SmwProgressStep(SmwState wizardState) {
    myWizardState = wizardState;
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    super._commit(finishChosen);
  }

  @Override
  public boolean canGoNext() {
    // TODO
    return true;
  }
}
