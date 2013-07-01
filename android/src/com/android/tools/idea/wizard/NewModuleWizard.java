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

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard {
  private static final Logger LOG = Logger.getInstance("#" + NewModuleWizard.class.getName());

  private NewModuleWizardState myWizardState;
  private ChooseTemplateStep myChooseModuleStep;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private TemplateParameterStep myTemplateParameterStep;
  private LauncherIconStep myLauncherIconStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityTemplateParameterStep;
  private boolean myInitializationComplete = false;

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    getWindow().setMinimumSize(new Dimension(800, 640));
    init();
  }

  @Override
  protected void init() {
    myWizardState = new NewModuleWizardState() {
      @Override
      public void setTemplateLocation(@NotNull File file) {
        super.setTemplateLocation(file);
        update();
      }
    };

    myChooseModuleStep = new ChooseTemplateStep(this, myWizardState, CATEGORY_PROJECTS);
    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(this, myWizardState);
    myTemplateParameterStep = new TemplateParameterStep(this, myWizardState);
    myLauncherIconStep = new LauncherIconStep(this, myWizardState.getLauncherIconState());
    myChooseActivityStep = new ChooseTemplateStep(this, myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES);
    myActivityTemplateParameterStep = new TemplateParameterStep(this, myWizardState.getActivityTemplateState());

    mySteps.add(myChooseModuleStep);
    mySteps.add(myConfigureAndroidModuleStep);
    mySteps.add(myTemplateParameterStep);
    mySteps.add(myLauncherIconStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityTemplateParameterStep);

    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, TemplateMetadata.GRADLE_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, TemplateMetadata.GRADLE_PLUGIN_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);

    myInitializationComplete = true;
    super.init();
  }

  @Override
  void update() {
    if (!myInitializationComplete) {
      return;
    }
    myConfigureAndroidModuleStep.setVisible(myWizardState.myIsAndroidModule);
    myTemplateParameterStep.setVisible(!myWizardState.myIsAndroidModule);
    myLauncherIconStep.setVisible(myWizardState.myIsAndroidModule && (Boolean)myWizardState.get(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible(
      myWizardState.myIsAndroidModule && (Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    myActivityTemplateParameterStep.setVisible(
      myWizardState.myIsAndroidModule && (Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createModule() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          populateDirectoryParameters(myWizardState);
          File projectRoot = new File(myProject.getBasePath());
          File moduleRoot = new File(projectRoot, (String)myWizardState.get(NewProjectWizardState.ATTR_MODULE_NAME));
          projectRoot.mkdirs();
          if (myLauncherIconStep.isStepVisible() && (Boolean)myWizardState.get(TemplateMetadata.ATTR_CREATE_ICONS)) {
            myWizardState.getLauncherIconState().outputImages(moduleRoot);
          }
          myWizardState.updateParameters();
          myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
          if (myActivityTemplateParameterStep.isStepVisible() && (Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY)) {
            myWizardState.getActivityTemplateState().getTemplate()
              .render(moduleRoot, moduleRoot, myWizardState.getActivityTemplateState().myParameters);
          }
          GradleProjectImporter.getInstance().reImportProject(myProject);
        } catch (Exception e) {
          Messages.showErrorDialog(e.getMessage(), "New Module Wizard");
          LOG.error(e);
        }
      }
    });
  }
}
