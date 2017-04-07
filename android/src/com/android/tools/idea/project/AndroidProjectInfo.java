/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidProjectInfo {
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidProjectInfo getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidProjectInfo.class);
  }

  public AndroidProjectInfo(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Indicates whether the given project has at least one module backed by an {@link AndroidProject}. To check if a project is a
   * "Gradle project," please use the method {@link GradleProjectInfo#isBuildWithGradle()}.
   *
   * @return {@code true} if the project has one or more modules backed by an {@link AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.requiresAndroidModel()) {
        return true;
      }
    }
    return false;
  }
}
