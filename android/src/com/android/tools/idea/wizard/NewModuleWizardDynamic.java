/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener;
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Lists;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;

/**
 * {@linkplain NewModuleWizard} guides the user through adding a new module to an existing project. It has a template-based flow and as the
 * first step of the wizard allows the user to choose a template which will guide the rest of the wizard flow.
 */
public class NewModuleWizardDynamic extends NewProjectWizardDynamic {
  private List<File> myFilesToOpen = Lists.newArrayList();

  public NewModuleWizardDynamic(@Nullable Project project, @Nullable Module module) {
    super(project, module);
    setTitle("Create New Module");
  }

  @Override
  public void init() {
    super.init();
    Project project = getProject();
    if (project != null) {
      getState().put(PROJECT_LOCATION_KEY, project.getBasePath());
    }
    ConfigureAndroidProjectPath.putSdkDependentParams(getState());
  }

  @Override
  protected void addPaths() {
    addPath(new SingleStepPath(new ChooseModuleTypeStep(getDisposable())));
    addPath(new SingleStepPath(new ConfigureAndroidModuleStepDynamic(getProject(), getDisposable())));
    for (NewFormFactorModulePath path : NewFormFactorModulePath.getAvailableFormFactorModulePaths(getDisposable())) {
      addPath(path);
    }
  }

  @Override
  protected String getWizardActionDescription() {
    return String.format("Create %1$s", getState().get(APPLICATION_NAME_KEY));
  }

  @Override
  public void performFinishingActions() {
    Project project = getProject();
    if (project == null) {
      return;
    }

    // Collect files to open
    for (AndroidStudioWizardPath path : myPaths) {
      if (path instanceof NewFormFactorModulePath) {
        myFilesToOpen.addAll(((NewFormFactorModulePath)path).getFilesToOpen());
      }
    }

    GradleProjectImporter.getInstance().requestProjectSync(project, new NewProjectImportGradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull final Project project) {
          openTemplateFiles(project);
      }

      private boolean openTemplateFiles(Project project) {
        return TemplateUtils.openEditors(project, myFilesToOpen, true);
      }
    });
  }
}
