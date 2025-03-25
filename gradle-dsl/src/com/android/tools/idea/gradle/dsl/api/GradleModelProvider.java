/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GradleModelProvider {

  @NotNull
  public static GradleModelProvider getInstance() {
    return ApplicationManager.getApplication().getService(GradleModelProvider.class);
  }

  @NotNull
  public abstract ProjectBuildModel getProjectModel(@NotNull Project project);

  @Nullable
  public abstract ProjectBuildModel getProjectModel(@NotNull Project hostProject, @NotNull String compositeRoot);

  @Nullable
  public abstract GradleBuildModel getBuildModel(@NotNull Project project);

  @Nullable
  public abstract GradleBuildModel getBuildModel(@NotNull Module module);

  @NotNull
  public abstract GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project);

  @NotNull
  public abstract GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName);

  @Nullable
  public abstract GradleSettingsModel getSettingsModel(@NotNull Project project);

  @NotNull
  public abstract GradleSettingsModel getSettingsModel(@NotNull VirtualFile settingsFile, @NotNull Project hostProject);

  @Nullable
  public abstract GradleSettingsModel getSettingsModel(@NotNull Project hostProject, @NotNull String compositeRoot);

  @NotNull
  public abstract GradleVersionCatalogView getVersionCatalogView(@NotNull Project hostProject);

  @Nullable
  public abstract GradleVersionCatalogView getVersionCatalogView(@NotNull Project hostProject, @NotNull String compositeRoot);
}
