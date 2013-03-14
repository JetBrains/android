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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * NewProjectWizard runs the wizard for creating entirely new Android projects. It takes the user
 * through steps to configure the project, setting its location and build parameters, and allows
 * the user to choose an activity to populate it. The wizard is template-driven, using templates
 * that live in the ADK.
 */
public class NewProjectWizard extends TemplateWizard {
  private static final Logger LOG = Logger.getInstance("#" + NewProjectWizard.class.getName());

  private NewProjectWizardState myWizardState;

  public NewProjectWizard() {
    super("New Project", (Project)null);
    init();
  }

  @Override
  protected void init() {
    myWizardState = new NewProjectWizardState();

    mySteps.add(new ConfigureProjectStep(this, myWizardState));
    mySteps.add(new LauncherIconStep(this, myWizardState.getLauncherIconState()));
    mySteps.add(new ChooseActivityStep(this, myWizardState));
    mySteps.add(new TemplateParameterStep(this, myWizardState.getActivityTemplateState()));

    super.init();
  }

  public void createProject() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          File contentRoot = new File((String)myWizardState.get(NewProjectWizardState.ATTR_PROJECT_LOCATION));
          contentRoot.mkdirs();
          if ((Boolean)myWizardState.get(TemplateMetadata.ATTR_CREATE_ICONS)) {
            myWizardState.getLauncherIconState().outputImages(contentRoot);
          }
          myWizardState.updateParameters();
          myWizardState.myTemplate.render(contentRoot, myWizardState.myParameters);
          if ((Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY)) {
            myWizardState.getActivityTemplateState().getTemplate()
              .render(contentRoot, myWizardState.getActivityTemplateState().myParameters);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  protected final int getNextStep(final int step) {
    for (int i = step + 1; i < mySteps.size(); i++) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }

  @Override
  protected final int getPreviousStep(final int step) {
    for (int i = step - 1; i >= 0; i--) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }
}
