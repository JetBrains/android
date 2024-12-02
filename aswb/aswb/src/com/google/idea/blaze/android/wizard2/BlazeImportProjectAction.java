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
package com.google.idea.blaze.android.wizard2;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.wizard2.BlazeEditProjectViewImportWizardStep;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectWizard;
import com.google.idea.blaze.base.wizard2.BlazeSelectWorkspaceImportWizardStep;
import com.google.idea.blaze.base.wizard2.BlazeSelectProjectViewImportWizardStep;
import com.google.idea.blaze.base.wizard2.ProjectImportWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import java.io.IOException;

class BlazeImportProjectAction extends AnAction {
  private static final Logger logger = Logger.getInstance(BlazeImportProjectAction.class);

  public BlazeImportProjectAction() {
    super();
    setRunInApplicationScope();
  }

  /** Explicitly set to run action in the application context (vs. project context) */
  private void setRunInApplicationScope() {
    getTemplatePresentation().setApplicationScope(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    BlazeNewProjectWizard wizard =
        new BlazeNewProjectWizard() {
          @Override
          protected ProjectImportWizardStep[] getSteps(WizardContext context) {
            return new ProjectImportWizardStep[] {
              new BlazeSelectWorkspaceImportWizardStep(context),
              new BlazeSelectProjectViewImportWizardStep(context),
              new BlazeEditProjectViewImportWizardStep(context)
            };
          }
        };
    if (!wizard.showAndGet()) {
      return;
    }
    BlazeProjectCreator projectCreator = new BlazeProjectCreator(wizard.builder);
    createFromWizard(projectCreator, wizard.context);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation()
        .setText(String.format("Import %s Project...", Blaze.defaultBuildSystemName()));
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static void createFromWizard(
      BlazeProjectCreator blazeProjectCreator, WizardContext wizardContext) {
    try {
      blazeProjectCreator.doCreate(
          wizardContext.getProjectFileDirectory(),
          wizardContext.getProjectName(),
          wizardContext.getProjectStorageFormat());
    } catch (final IOException e) {
      logger.error("Project creation failed", e);
      ApplicationManager.getApplication()
          .invokeLater(
              () -> Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed"));
    }
  }
}
