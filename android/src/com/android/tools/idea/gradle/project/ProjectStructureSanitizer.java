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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.sdk.DefaultSdks;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;

public class ProjectStructureSanitizer {
  @NotNull private final Project myProject;

  @NotNull
  public static ProjectStructureSanitizer getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectStructureSanitizer.class);
  }

  public ProjectStructureSanitizer(@NotNull Project project) {
    myProject = project;
  }

  public void cleanUp() {
    ensureAllModulesHaveSdk();
    Projects.enforceExternalBuild(myProject);
    AndroidGradleProjectComponent.getInstance(myProject).checkForSupportedModules();
    generateSourcesOnly();
  }

  private void ensureAllModulesHaveSdk() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      ModifiableRootModel model = moduleRootManager.getModifiableModel();
      try {
        if (model.getSdk() == null) {
          Sdk jdk = DefaultSdks.getDefaultJdk();
          model.setSdk(jdk);
        }
      }
      finally {
        model.commit();
      }
    }
  }

  private void generateSourcesOnly() {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          ProjectBuilder.getInstance(myProject).generateSourcesOnly();
        }
      });
    }
  }
}
