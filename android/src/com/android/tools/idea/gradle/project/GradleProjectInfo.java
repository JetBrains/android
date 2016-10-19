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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;

public class GradleProjectInfo {
  @NotNull private final Project myProject;

  @NotNull
  public static GradleProjectInfo getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleProjectInfo.class);
  }

  public GradleProjectInfo(@NotNull Project project) {
    myProject = project;
  }

  public boolean hasTopLevelGradleBuildFile() {
    File projectFolderPath = getBaseDirPath(myProject);
    File buildFilePath = new File(projectFolderPath, FN_BUILD_GRADLE);
    return buildFilePath.isFile();
  }

  /**
   * Indicates whether Gradle is used to build at least one module in this project.
   * Note: {@link Projects#requiresAndroidModel(Project)} indicates whether a project requires an {@link AndroidModel}.
   * That method should be preferred in almost all cases. Use this method only if you explicitly need to check whether the model is
   * Gradle-specific.
   */
  public boolean isBuildWithGradle() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      if (Projects.isBuildWithGradle(module)) {
        return true;
      }
    }
    // See https://code.google.com/p/android/issues/detail?id=203384
    // This could be a project without modules. Check that at least it synced with Gradle.
    return GradleSyncState.getInstance(myProject).getSummary().getSyncTimestamp() != -1L;
  }
}
