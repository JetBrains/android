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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.wizard.NewProjectWizardState.ATTR_MODULE_NAME;

public class TemplateWizardModuleBuilder extends ModuleBuilder implements TemplateWizardStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance("#" + TemplateWizardModuleBuilder.class.getName());
  private final TemplateMetadata myMetadata;
  @NotNull final List<ModuleWizardStep> mySteps;
  private Project myProject;

  NewModuleWizardState myWizardState;
  ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  TemplateParameterStep myTemplateParameterStep;
  LauncherIconStep myLauncherIconStep;
  ChooseTemplateStep myChooseActivityStep;
  TemplateParameterStep myActivityTemplateParameterStep;
  boolean myInitializationComplete = false;

  public TemplateWizardModuleBuilder(@Nullable File templateFile,
                                     @Nullable TemplateMetadata metadata,
                                     @NotNull Project project,
                                     @Nullable Icon sidePanelIcon,
                                     @NotNull List<ModuleWizardStep> steps,
                                     boolean hideModuleName) {
    myMetadata = metadata;
    myProject = project;
    mySteps = steps;

    myWizardState = new NewModuleWizardState() {
      @Override
      public void setTemplateLocation(@NotNull File file) {
        super.setTemplateLocation(file);
        update();
      }
    };

    if (templateFile != null) {
      myWizardState.setTemplateLocation(templateFile);
    }
    if (hideModuleName) {
      myWizardState.myHidden.add(ATTR_MODULE_NAME);
    }

    myWizardState.convertApisToInt();

    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(myWizardState, myProject, sidePanelIcon, this);
    myTemplateParameterStep = new TemplateParameterStep(myWizardState, myProject, sidePanelIcon, this);
    myLauncherIconStep = new LauncherIconStep(myWizardState.getLauncherIconState(), myProject, sidePanelIcon, this);
    myChooseActivityStep = new ChooseTemplateStep(myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES, myProject, sidePanelIcon,
                                                  this, null);
    myActivityTemplateParameterStep = new TemplateParameterStep(myWizardState.getActivityTemplateState(), myProject, sidePanelIcon, this);

    mySteps.add(myConfigureAndroidModuleStep);
    mySteps.add(myTemplateParameterStep);
    mySteps.add(myLauncherIconStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityTemplateParameterStep);

    myWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GradleUtil.GRADLE_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);

    update();

    myInitializationComplete = true;
  }

  @Nullable
  @Override
  public String getBuilderId() {
    assert myMetadata != null;
    return myMetadata.getTitle();
  }

  @Override
  @NotNull
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return mySteps.toArray(new ModuleWizardStep[mySteps.size()]);
  }

  @Override
  public void update() {
    if (!myInitializationComplete) {
      return;
    }
    myConfigureAndroidModuleStep.setVisible(myWizardState.myIsAndroidModule);
    myTemplateParameterStep.setVisible(!myWizardState.myIsAndroidModule);
    myLauncherIconStep.setVisible(myWizardState.myIsAndroidModule && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible(
      myWizardState.myIsAndroidModule && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myActivityTemplateParameterStep.setVisible(
      myWizardState.myIsAndroidModule && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
  }

  @Override
  public void setupRootModel(final @NotNull ModifiableRootModel rootModel) throws ConfigurationException {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  createModule();
                }
              });
            }
          });
        }
      });
    }

  @Override
  @NotNull
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  @Override
  public void setName(@NotNull String name) {
    super.setName(name);
    myConfigureAndroidModuleStep.setModuleName(name);
  }

  public void createModule() {
    try {
      myWizardState.populateDirectoryParameters();
      File projectRoot = new File(myProject.getBasePath());
      File moduleRoot = new File(projectRoot, myWizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME));
      // TODO: handle return type of "mkdirs".
      projectRoot.mkdirs();
      if (myLauncherIconStep.isStepVisible() && myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS)) {
        myWizardState.getLauncherIconState().outputImages(moduleRoot);
      }
      myWizardState.updateParameters();
      myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
      if (myActivityTemplateParameterStep.isStepVisible() && myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
        TemplateWizardState activityTemplateState = myWizardState.getActivityTemplateState();
        Template template = activityTemplateState.getTemplate();
        assert template != null;
        template.render(moduleRoot, moduleRoot, activityTemplateState.myParameters);
      }
      GradleProjectImporter.getInstance().reImportProject(myProject, null);
    } catch (Exception e) {
      Messages.showErrorDialog(e.getMessage(), "New Module");
      LOG.error(e);
    }
  }
}
