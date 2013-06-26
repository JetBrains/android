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

import com.android.tools.idea.gradle.GradleProjectImporter;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.io.Closeables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;

/**
 * NewProjectWizard runs the wizard for creating entirely new Android projects. It takes the user
 * through steps to configure the project, setting its location and build parameters, and allows
 * the user to choose an activity to populate it. The wizard is template-driven, using templates
 * that live in the ADK.
 */
public class NewProjectWizard extends TemplateWizard {
  private static final Logger LOG = Logger.getInstance("#" + NewProjectWizard.class.getName());
  private static final String ATTR_GRADLE_DISTRIBUTION_URL = "distributionUrl";

  private NewProjectWizardState myWizardState;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private LauncherIconStep myLauncherIconStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityParameterStep;
  private boolean myInitializationComplete = false;

  public NewProjectWizard() {
    super("New Project", (Project)null);
    getWindow().setMinimumSize(new Dimension(800, 640));
    init();
  }

  @Override
  protected void init() {
    if (!TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg = "<html>Your Android SDK is out of date or is missing templates. Please ensure you are using SDK version 22 or later.<br>"
        + "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException(msg);
    }
    myWizardState = new NewProjectWizardState();
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, TemplateMetadata.GRADLE_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, TemplateMetadata.GRADLE_PLUGIN_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);

    myConfigureAndroidModuleStep = new ConfigureAndroidModuleStep(this, myWizardState);
    myLauncherIconStep = new LauncherIconStep(this, myWizardState.getLauncherIconState());
    myChooseActivityStep = new ChooseTemplateStep(this, myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES);
    myActivityParameterStep = new TemplateParameterStep(this, myWizardState.getActivityTemplateState());

    mySteps.add(myConfigureAndroidModuleStep);
    mySteps.add(myLauncherIconStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityParameterStep);

    myInitializationComplete = true;
    super.init();
  }

  @Override
  void update() {
    if (!myInitializationComplete) {
      return;
    }
    myLauncherIconStep.setVisible((Boolean)myWizardState.get(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible((Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    myActivityParameterStep.setVisible((Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  @Override
  public Icon getSidePanelIcon() {
    return AndroidIcons.Wizards.NewProjectSidePanel;
  }

  public void createProject() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          populateDirectoryParameters(myWizardState);
          String projectName = (String)myWizardState.get(NewProjectWizardState.ATTR_MODULE_NAME);
          File projectRoot = new File((String)myWizardState.get(NewProjectWizardState.ATTR_PROJECT_LOCATION));
          File moduleRoot = new File(projectRoot, projectName);
          projectRoot.mkdirs();
          createGradleWrapper(projectRoot);
          if ((Boolean)myWizardState.get(TemplateMetadata.ATTR_CREATE_ICONS)) {
            myWizardState.getLauncherIconState().outputImages(moduleRoot);
          }
          myWizardState.updateParameters();
          myWizardState.myTemplate.render(projectRoot, moduleRoot, myWizardState.myParameters);
          if ((Boolean)myWizardState.get(NewProjectWizardState.ATTR_CREATE_ACTIVITY)) {
            myWizardState.getActivityTemplateState().getTemplate()
              .render(moduleRoot, moduleRoot, myWizardState.getActivityTemplateState().myParameters);
          }
          Sdk sdk = getSdk((Integer)myWizardState.get(ATTR_BUILD_API));
          GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
          projectImporter.importProject(projectName, projectRoot, sdk, null);
        }
        catch (Exception e) {
          String title;
          if (e instanceof ConfigurationException) {
            title = ((ConfigurationException)e).getTitle();
          } else {
            title = "New Project Wizard";
          }
          Messages.showErrorDialog(e.getMessage(), title);
          LOG.error(e);
        }
      }
    });
  }

  private void createGradleWrapper(File projectRoot) throws IOException {
    FileOutputStream os = null;
    try {
      File gradleWrapperSrc = new File(TemplateManager.getTemplateRootFolder(), GRADLE_WRAPPER_PATH);
      FileUtil.copyDirContent(gradleWrapperSrc, projectRoot);
      File gradleWrapperProperties = new File(projectRoot, GRADLE_WRAPPER_PROPERTIES_PATH);
      Properties wrapperProperties = new Properties();
      wrapperProperties.load(new FileInputStream(gradleWrapperProperties));
      wrapperProperties.put(ATTR_GRADLE_DISTRIBUTION_URL, TemplateMetadata.GRADLE_DISTRIBUTION_URL);
      os = new FileOutputStream(gradleWrapperProperties);
      wrapperProperties.store(os, "");
    } finally {
      Closeables.closeQuietly(os);
    }
  }
}
