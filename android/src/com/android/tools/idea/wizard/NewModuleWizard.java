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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard implements ChooseTemplateStep.TemplateChangeListener {
  private static final Logger LOG = Logger.getInstance("#" + NewModuleWizard.class.getName());

  private ChooseTemplateStep myChooseModuleStep;
  private TemplateWizardModuleBuilder myModuleBuilder;

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    getWindow().setMinimumSize(new Dimension(800, 640));
    init();
  }

  @Override
  protected void init() {
    myModuleBuilder = new TemplateWizardModuleBuilder(null, null, myProject, AndroidIcons.Wizards.NewModuleSidePanel, mySteps, false) {
      @Override
      public void update() {
        super.update();
        NewModuleWizard.this.update();
      }
    };

    myChooseModuleStep = new ChooseTemplateStep(myModuleBuilder.myWizardState, CATEGORY_PROJECTS, myProject,
                                                AndroidIcons.Wizards.NewModuleSidePanel, myModuleBuilder, this);
    myModuleBuilder.mySteps.add(0, myChooseModuleStep);
    super.init();
  }

  @Override
  public void update() {
    if (myModuleBuilder == null || !myModuleBuilder.myInitializationComplete) {
      return;
    }
    NewModuleWizardState wizardState = myModuleBuilder.myWizardState;
    myModuleBuilder.myConfigureAndroidModuleStep.setVisible(wizardState.myIsAndroidModule);
    myModuleBuilder.myTemplateParameterStep.setVisible(!wizardState.myIsAndroidModule);
    myModuleBuilder.myLauncherIconStep.setVisible(wizardState.myIsAndroidModule &&
                                                  (Boolean)wizardState.get(TemplateMetadata.ATTR_CREATE_ICONS));
    myModuleBuilder.myChooseActivityStep.setVisible(wizardState.myIsAndroidModule &&
                                                    (Boolean)wizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    myModuleBuilder.myActivityTemplateParameterStep.setVisible(wizardState.myIsAndroidModule &&
                                                               (Boolean)wizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myModuleBuilder.createModule();
      }
    });
  }

  @Override
  public void templateChanged() {
    myModuleBuilder.myConfigureAndroidModuleStep.refreshUiFromParameters();
  }
}
