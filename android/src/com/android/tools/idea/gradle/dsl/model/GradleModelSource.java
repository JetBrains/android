/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleModelSource extends GradleModelProvider {

  @NotNull
  @Override
  public ProjectBuildModel getProjectModel(@NotNull Project project) {
    VirtualFile file = getGradleBuildFile(getBaseDirPath(project));
    return new ProjectBuildModelImpl(project, file);
  }

  @Override
  @Nullable
  public ProjectBuildModel getProjectModel(@NotNull Project hostProject, @NotNull String compositeRoot) {
    VirtualFile file = getGradleBuildFile(new File(compositeRoot));
    if (file == null) {
      return null;
    }

    return new ProjectBuildModelImpl(hostProject, file);
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Project project) {
    VirtualFile file = getGradleBuildFile(getBaseDirPath(project));
    return file != null ? internalCreateBuildModel(file, project, project.getName()) : null;
  }

  @Nullable
  @Override
  public GradleBuildModel getBuildModel(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? internalCreateBuildModel(file, module.getProject(), module.getName()) : null;
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return internalCreateBuildModel(file, project, "<Unknown>");
  }

  @NotNull
  @Override
  public GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    return internalCreateBuildModel(file, project, moduleName);
  }

  @Nullable
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    return file != null ? parseSettingsFile(file, project, "settings") : null;
  }

  @NotNull
  @Override
  public GradleSettingsModel getSettingsModel(@NotNull VirtualFile settingsFile, @NotNull Project hostProject) {
    return parseSettingsFile(settingsFile, hostProject, "settings");
  }

  @NotNull
  private static GradleBuildModel internalCreateBuildModel(@NotNull VirtualFile file,
                                                          @NotNull Project project,
                                                          @NotNull String moduleName) {
    return new GradleBuildModelImpl(BuildModelContext.create(project).getOrCreateBuildFile(file, moduleName, false));
  }

  /**
   * This method is left here to ensure that when needed we can construct a settings model with only the virtual file.
   * In most cases {@link GradleSettingsModel}s should be obtained from the {@link ProjectBuildModel}.
   */
  @NotNull
  private static GradleSettingsModel parseSettingsFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleSettingsFile settingsFile = new GradleSettingsFile(file, project, moduleName, BuildModelContext.create(project));
    settingsFile.parse();
    return new GradleSettingsModelImpl(settingsFile);
  }
}
