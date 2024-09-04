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

import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.options.ConfigurationException;
import javax.annotation.Nullable;
import javax.swing.Icon;

abstract class ProjectImportWizardStep extends StepAdapter {
  private final WizardContext myContext;

  ProjectImportWizardStep(WizardContext context) {
    myContext = context;
  }

  @Override
  public Icon getIcon() {
    return myContext.getStepIcon();
  }

  protected ProjectBuilder getBuilder() {
    return myContext.getProjectBuilder();
  }

  WizardContext getWizardContext() {
    return myContext;
  }

  @Nullable
  public abstract String getHelpId();

  public boolean isStepVisible() {
    return true;
  }

  /** Update UI from BlazeNewProjectBuilder and WizardContext */
  public abstract void updateStep();

  /**
   * Validates the current selection. If there are no problems, commits data from UI into
   * BlazeNewProjectBuilder and WizardContext, else throws {@link ConfigurationException} with an
   * error message for the user or {@link
   * com.intellij.openapi.options.CancelledConfigurationException} to return to the wizard without
   * an error.
   */
  public abstract void validateAndUpdateModel() throws ConfigurationException;

  public abstract void onWizardFinished() throws CommitStepException;

  protected BlazeNewProjectBuilder getProjectBuilder() {
    BlazeProjectImportBuilder builder =
        (BlazeProjectImportBuilder) getWizardContext().getProjectBuilder();
    assert builder != null;
    return builder.builder();
  }
}
