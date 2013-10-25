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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.templates.Template.CATEGORY_ACTIVITIES;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static icons.AndroidIcons.Wizards.NewProjectSidePanel;

/**
 * NewProjectWizard runs the wizard for creating entirely new Android projects. It takes the user
 * through steps to configure the project, setting its location and build parameters, and allows
 * the user to choose an activity to populate it. The wizard is template-driven, using templates
 * that live in the ADK.
 */
public class NewProjectWizard extends TemplateWizard implements TemplateParameterStep.UpdateListener {
  private static final Logger LOG = Logger.getInstance("#" + NewProjectWizard.class.getName());
  private static final String JAVA_SRC_PATH = FD_SOURCES + File.separator + FD_MAIN + File.separator + FD_JAVA;
  private static final String ERROR_MSG_TITLE = "New Project Wizard";
  private static final String UNABLE_TO_CREATE_DIR_FORMAT = "Unable to create directory '%s1$s'.";

  private NewProjectWizardState myWizardState;
  private LauncherIconStep myLauncherIconStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityParameterStep;
  private boolean myInitializationComplete = false;

  public NewProjectWizard() {
    super("New Project", null);
    getWindow().setMinimumSize(new Dimension(1000, 640));
    init();
  }

  @Override
  protected void init() {
    if (!TemplateManager.templatesAreValid()) {
      String title = "SDK problem";
      String msg = "<html>Your Android SDK is out of date or is missing templates. Please ensure you are using SDK version 22 or later.<br>"
        + "You can configure your SDK via <b>Configure | Project Defaults | Project Structure | SDKs</b></html>";
      super.init();
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException(msg);
    }
    myWizardState = new NewProjectWizardState();
    myWizardState.convertApisToInt();
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GradleUtil.GRADLE_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GradleUtil.GRADLE_PLUGIN_LATEST_VERSION);
    myWizardState.put(TemplateMetadata.ATTR_V4_SUPPORT_LIBRARY_VERSION, TemplateMetadata.V4_SUPPORT_LIBRARY_VERSION);

    ConfigureAndroidModuleStep configureAndroidModuleStep =
      new ConfigureAndroidModuleStep(myWizardState, myProject, NewProjectSidePanel, this);
    configureAndroidModuleStep.updateStep();
    myLauncherIconStep = new LauncherIconStep(myWizardState.getLauncherIconState(), myProject, NewProjectSidePanel, this);
    myChooseActivityStep = new ChooseTemplateStep(myWizardState.getActivityTemplateState(), CATEGORY_ACTIVITIES, myProject,
                                                  NewProjectSidePanel, this, null);
    myActivityParameterStep = new TemplateParameterStep(myWizardState.getActivityTemplateState(), myProject, NewProjectSidePanel, this);

    mySteps.add(configureAndroidModuleStep);
    mySteps.add(myLauncherIconStep);
    mySteps.add(myChooseActivityStep);
    mySteps.add(myActivityParameterStep);

    myInitializationComplete = true;
    super.init();
  }

  @Override
  public void update() {
    if (!myInitializationComplete) {
      return;
    }
    myLauncherIconStep.setVisible(myWizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS));
    myChooseActivityStep.setVisible(myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    myActivityParameterStep.setVisible(myWizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY));
    super.update();
  }

  public void createProject() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        createProject(myWizardState, null);
      }
    });
  }

  public static void createProject(@NotNull final NewModuleWizardState wizardState, @Nullable Project project) {
    List<String> errors = Lists.newArrayList();
    try {
      wizardState.populateDirectoryParameters();
      String projectName = wizardState.getString(NewProjectWizardState.ATTR_MODULE_NAME);
      File projectRoot = new File(wizardState.getString(NewModuleWizardState.ATTR_PROJECT_LOCATION));
      File moduleRoot = new File(projectRoot, projectName);
      if (!FileUtilRt.createDirectory(projectRoot)) {
        errors.add(String.format(UNABLE_TO_CREATE_DIR_FORMAT, projectRoot.getPath()));
      }
      createGradleWrapper(projectRoot);
      int apiLevel = wizardState.getInt(ATTR_BUILD_API);
      Sdk sdk = getSdk(apiLevel);
      if (sdk == null) {
        // This will NEVER happen. The SDK has been already set before this wizard runs.
        errors.add(String.format("Unable to find an Android SDK with API level %d.", apiLevel));
      }
      else {
        LocalProperties localProperties = new LocalProperties(projectRoot);
        localProperties.setAndroidSdkPath(sdk);
        localProperties.save();
      }
      if (wizardState.getBoolean(TemplateMetadata.ATTR_CREATE_ICONS)) {
        wizardState.getLauncherIconState().outputImages(moduleRoot);
      }
      wizardState.updateParameters();
      wizardState.updateDependencies();
      wizardState.myTemplate.render(projectRoot, moduleRoot, wizardState.myParameters);
      if (wizardState.getBoolean(NewModuleWizardState.ATTR_CREATE_ACTIVITY)) {
        TemplateWizardState activityTemplateState = wizardState.getActivityTemplateState();
        Template template = activityTemplateState.getTemplate();
        assert template != null;
        template.render(moduleRoot, moduleRoot, activityTemplateState.myParameters);
        wizardState.myTemplate.getFilesToOpen().addAll(template.getFilesToOpen());
      }
      else {
        // Ensure that at least the Java source directory exists. We could create other directories but this is the most used.
        // TODO: We should perhaps instantiate this from the Freemarker template, using the mkdir command
        File javaSrcDir = new File(moduleRoot, JAVA_SRC_PATH);
        File packageDir = new File(javaSrcDir, wizardState.getString(ATTR_PACKAGE_NAME).replace('.', File.separatorChar));
        if (!FileUtilRt.createDirectory(packageDir)) {
          errors.add(String.format(UNABLE_TO_CREATE_DIR_FORMAT, packageDir.getPath()));
        }
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }
      GradleProjectImporter projectImporter = GradleProjectImporter.getInstance();
      projectImporter.importProject(projectName, projectRoot, new GradleProjectImporter.Callback() {
        @Override
        public void projectImported(@NotNull Project project) {
          TemplateUtils.openEditors(project, wizardState.myTemplate.getFilesToOpen(), true);
        }

        @Override
        public void importFailed(@NotNull Project project, @NotNull final String errorMessage) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(errorMessage, ERROR_MSG_TITLE);
            }
          });
        }
      }, project);
    }
    catch (Exception e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
      }
      Messages.showErrorDialog(e.getMessage(), ERROR_MSG_TITLE);
      LOG.error(e);
    }
    if (!errors.isEmpty()) {
      String msg = errors.size() == 1 ? errors.get(0) : Joiner.on('\n').join(errors);
      Messages.showErrorDialog(msg, ERROR_MSG_TITLE);
      LOG.error(msg);
    }
  }

  @VisibleForTesting
  public static void createGradleWrapper(File projectRoot) throws IOException {
    File gradleWrapperSrc = new File(TemplateManager.getTemplateRootFolder(), GRADLE_WRAPPER_PATH);
    if (!gradleWrapperSrc.exists()) {
      for (File root : TemplateManager.getExtraTemplateRootFolders()) {
        gradleWrapperSrc = new File(root, GRADLE_WRAPPER_PATH);
        if (gradleWrapperSrc.exists()) {
          break;
        } else {
          gradleWrapperSrc = null;
        }
      }
    }
    if (gradleWrapperSrc == null) {
      return;
    }
    FileUtil.copyDirContent(gradleWrapperSrc, projectRoot);
    File wrapperPropertiesFile = GradleUtil.getGradleWrapperPropertiesFilePath(projectRoot);
    GradleUtil.updateGradleDistributionUrl(GradleUtil.GRADLE_LATEST_VERSION, wrapperPropertiesFile);
  }
}
