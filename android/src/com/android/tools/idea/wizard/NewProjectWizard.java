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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.NewAndroidProjectImporter;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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

  private NewProjectWizardState myWizardState;
  private ConfigureAndroidModuleStep myConfigureAndroidModuleStep;
  private LauncherIconStep myLauncherIconStep;
  private ChooseTemplateStep myChooseActivityStep;
  private TemplateParameterStep myActivityParameterStep;
  private boolean myInitializationComplete = false;

  public NewProjectWizard() {
    super("New Project", (Project)null);
    init();
  }

  @Override
  protected void init() {
    myWizardState = new NewProjectWizardState();

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

  public void createProject() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          populateDirectoryParameters(myWizardState);
          String projectName = (String)myWizardState.get(NewProjectWizardState.ATTR_PROJECT_NAME);
          File projectRoot = new File((String)myWizardState.get(NewProjectWizardState.ATTR_PROJECT_LOCATION));
          File moduleRoot = new File(projectRoot, projectName);
          File gradleWrapperSrc = new File(TemplateManager.getTemplateRootFolder(), GRADLE_WRAPPER_PATH);
          projectRoot.mkdirs();
          FileUtil.copyDirContent(gradleWrapperSrc, projectRoot);
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
          new NewAndroidProjectImporter().importProject(projectName, projectRoot, sdk, null);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Nullable
  private Sdk getSdk(int apiLevel) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      AndroidPlatform androidPlatform = AndroidPlatform.parse(sdk);
      if (androidPlatform != null) {
        AndroidSdkData sdkData = androidPlatform.getSdkData();
        IAndroidTarget target = sdkData.findTargetByApiLevel(Integer.toString(apiLevel));
        if (target != null) {
          return sdk;
        }
      }
    }
    return null;
  }
}
