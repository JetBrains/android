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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.awt.*;
import java.io.File;
import java.util.Collection;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizard extends TemplateWizard implements ExternalProjectRefreshCallback {
  private static final Logger LOG = Logger.getInstance("#" + NewModuleWizard.class.getName());

  private NewModuleWizardState myWizardState;
  private Project myProject;
  private ChooseTemplateStep myChooseModuleStep;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private TemplateParameterStep myTemplateParameterStep;
  private LauncherIconStep myLauncherIconStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityTemplateParameterStep;
  private boolean myInitializationComplete = false;

  public NewModuleWizard(@Nullable Project project) {
    super("New Module", project);
    myProject = project;
    getWindow().setMinimumSize(new Dimension(800,640));
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

    myWizardState.put(NewProjectWizardState.ATTR_PROJECT_LOCATION, myProject.getBasePath());

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
          File moduleBuildFile = new File(projectRoot, "build.gradle");
          ExternalSystemUtil
            .refreshProject(myProject, GradleConstants.SYSTEM_ID, moduleBuildFile.getPath(), NewModuleWizard.this, true, true);
        } catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
    ExternalSystemApiUtil.executeProjectChangeAction(myProject, GradleConstants.SYSTEM_ID, myProject, new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(new Runnable() {
          @Override
          public void run() {
            Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE);
            ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
            dataManager.importData(ProjectKeys.MODULE, modules, myProject, true);
          }
        });
      }
    });
  }

  @Override
  public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    throw new IllegalStateException(errorDetails == null ? "unknown error" : "");
  }
}
