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

import com.google.idea.blaze.base.help.BlazeHelpHandler;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.options.CancelledConfigurationException;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import java.awt.event.ActionListener;
import javax.annotation.Nullable;

/** Largely copied from AbstractProjectWizard / AddModuleWizard (which aren't in the CLion SDK). */
abstract class BlazeNewProjectWizard extends AbstractWizard<ProjectImportWizardStep> {

  final WizardContext context;
  final BlazeProjectImportBuilder builder;

  public BlazeNewProjectWizard() {
    super("Import Project from " + Blaze.defaultBuildSystemName(), (Project) null);

    builder = new BlazeProjectImportBuilder();
    context = new WizardContext(null, getDisposable());
    context.putUserData(AbstractWizard.KEY, this);
    context.setProjectBuilder(builder);
    for (ProjectImportWizardStep step : getSteps(context)) {
      addStep(step);
    }
    init();
  }

  protected abstract ProjectImportWizardStep[] getSteps(WizardContext context);

  @Override
  protected void helpAction() {
    doHelpAction();
  }

  @Override
  protected void doHelpAction() {
    String helpId = getHelpID();
    BlazeHelpHandler helpHandler = BlazeHelpHandler.getInstance();
    if (helpId != null && helpHandler != null) {
      helpHandler.handleHelp(helpId);
    }
  }

  @Nullable
  @Override
  protected String getHelpID() {
    ProjectImportWizardStep step = getCurrentStepObject();
    if (step != null) {
      return step.getHelpId();
    }
    return null;
  }

  // Swallow the escape key
  @Nullable
  @Override
  protected ActionListener createCancelAction() {
    return null;
  }

  @Override
  protected void updateStep() {
    if (!mySteps.isEmpty()) {
      getCurrentStepObject().updateStep();
    }
    super.updateStep();
    myIcon.setIcon(null);
  }

  @Override
  protected final void doOKAction() {
    final Ref<Boolean> result = Ref.create(false);
    DumbService.allowStartingDumbModeInside(
        DumbModePermission.MAY_START_BACKGROUND, () -> result.set(doFinishAction()));
    if (!result.get()) {
      return;
    }
    super.doOKAction();
  }

  private boolean doFinishAction() {
    int idx = getCurrentStep();
    try {
      do {
        ProjectImportWizardStep step = mySteps.get(idx);
        if (step != getCurrentStepObject()) {
          step.updateStep();
        }
        if (!commitStepData(step)) {
          return false;
        }
        try {
          step._commit(true);
        } catch (CommitStepException e) {
          handleCommitException(e);
          return false;
        }
        if (!isLastStep(idx)) {
          idx = getNextStep(idx);
        } else {
          for (ProjectImportWizardStep wizardStep : mySteps) {
            try {
              wizardStep.onWizardFinished();
            } catch (CommitStepException e) {
              handleCommitException(e);
              return false;
            }
          }
          break;
        }
      } while (true);
    } finally {
      myCurrentStep = idx;
      updateStep();
    }
    return true;
  }

  private boolean commitStepData(ProjectImportWizardStep step) {
    try {
      step.validateAndUpdateModel();
      return true;
    } catch (ConfigurationException e) {
      if (!(e instanceof CancelledConfigurationException)) {
        Messages.showErrorDialog(myContentPanel, e.getMessage(), e.getTitle());
      }
      return false;
    }
  }

  @Override
  public void doNextAction() {
    if (!commitStepData(getCurrentStepObject())) {
      return;
    }
    super.doNextAction();
  }

  private void handleCommitException(CommitStepException e) {
    String message = e.getMessage();
    if (message != null) {
      Messages.showErrorDialog(getCurrentStepComponent(), message);
    }
  }

  @Override
  protected boolean isLastStep() {
    return isLastStep(getCurrentStep());
  }

  private boolean isLastStep(int step) {
    return getNextStep(step) == step;
  }

  @Override
  protected int getNextStep(int stepIndex) {
    int nextIndex = stepIndex + 1;
    while (nextIndex < mySteps.size()) {
      ProjectImportWizardStep nextStep = mySteps.get(nextIndex);
      if (nextStep.isStepVisible()) {
        return nextIndex;
      }
      nextIndex++;
    }
    return stepIndex;
  }

  @Override
  protected int getPreviousStep(int stepIndex) {
    int prevIndex = stepIndex - 1;
    while (prevIndex >= 0) {
      ProjectImportWizardStep prevStep = mySteps.get(prevIndex);
      if (prevStep.isStepVisible()) {
        return prevIndex;
      }
      prevIndex--;
    }
    return stepIndex;
  }
}
