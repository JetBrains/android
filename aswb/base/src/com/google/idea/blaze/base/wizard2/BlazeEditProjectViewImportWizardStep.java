/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.wizard2.ui.BlazeEditProjectViewControl;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.options.ConfigurationException;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Shows the edit project view screen. */
class BlazeEditProjectViewImportWizardStep extends ProjectImportWizardStep {

  private final JPanel component = new JPanel(new BorderLayout());
  private BlazeEditProjectViewControl control;
  private boolean settingsInitialised;

  public BlazeEditProjectViewImportWizardStep(WizardContext context) {
    super(context);
  }

  @Override
  public JComponent getComponent() {
    return component;
  }

  @Override
  public void updateStep() {
    if (!settingsInitialised) {
      init();
    } else {
      control.update(getProjectBuilder());
    }
  }

  private void init() {
    control =
        new BlazeEditProjectViewControl(getProjectBuilder(), getWizardContext().getDisposable());
    this.component.add(control.getUiComponent());
    settingsInitialised = true;
  }

  @Override
  public void validateAndUpdateModel() throws ConfigurationException {
    BlazeValidationResult validationResult = control.validate();
    validationResult.throwConfigurationExceptionIfNotSuccess();

    BlazeNewProjectBuilder builder = getProjectBuilder();
    control.updateBuilder(builder);

    WizardContext wizardContext = getWizardContext();
    wizardContext.setProjectName(builder.getProjectName());
    wizardContext.setProjectFileDirectory(builder.getProjectDataDirectory());
  }

  @Override
  public void onWizardFinished() throws CommitStepException {
    try {
      getProjectBuilder().commit();
      control.commit();
    } catch (BlazeProjectCommitException e) {
      throw new CommitStepException(e.getMessage());
    }
  }

  @Override
  public String getHelpId() {
    return "docs/project-views";
  }
}
