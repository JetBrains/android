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

import com.android.tools.idea.gradle.NewAndroidProjectImporter;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.run.TargetSelectionMode;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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
    getWindow().setMinimumSize(new Dimension(800, 640));
    init();
  }

  @Override
  protected void init() {
    // Do a sanity check to see if we have templates that look compatible, otherwise we get really strange problems. The existence
    // of a gradle wrapper in the templates directory is a good sign.
    boolean exists = false;
    try { exists = new File(TemplateManager.getTemplateRootFolder(), "gradle/wrapper/gradlew").exists(); } catch (Exception e) {}
    if (!exists) {
      String title = "SDK problem";
      String msg = "Your Android SDK is out of date or is missing templates. Please ensure you are using SDK version 22 or later.";
      Messages.showErrorDialog(msg, title);
      throw new IllegalStateException(msg);
    }
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
          new NewAndroidProjectImporter().importProject(projectName, projectRoot, sdk, new ImportCompletedCallback());
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

  private static class ImportCompletedCallback implements NewAndroidProjectImporter.Callback {
    @Override
    public void projectImported(@NotNull final Project project) {
      // The project imported callback from gradle is not guaranteed to be called after IDEA actually
      // knows about all the modules. So wrap all necessary activities in a post startup runnable.
      StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              // Automatically create a run configuration.
              // TODO: The IDEA NPW actually had a page where they ask users about the parameters
              // for the launch configuration, but the Android NPW doesn't, so we just add one that
              // always pops up the device chooser dialog.
              final AndroidFacet facet = findAndroidFacet(project);
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  if (facet != null) {
                    AndroidUtils.addRunConfiguration(facet, null, false, TargetSelectionMode.SHOW_DIALOG, null);
                  }
                }
              });
            }
          });
        }
      });
    }

    /** Returns the Android Facet from the first module in the project that has an Android Facet. */
    @Nullable
    private static AndroidFacet findAndroidFacet(Project project) {
      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module m : modules) {
        AndroidFacet facet = AndroidFacet.getInstance(m);
        if (facet != null) {
          return facet;
        }
      }

      return null;
    }
  }
}
