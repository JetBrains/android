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

import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles generic build system operations such as syncing and building.
 */
public abstract class BuildSystemService {

  private static final ExtensionPointName<BuildSystemService> EP_NAME =
    new ExtensionPointName<>("com.android.project.buildSystemService");

  @Nullable
  public static BuildSystemService getInstance(@NotNull Project project) {
    for (BuildSystemService extension : EP_NAME.getExtensions()) {
      if (extension.isApplicable(project)) {
        return extension;
      }
    }
    return null;
  }

  protected abstract boolean isApplicable(@NotNull Project project);

  public abstract void buildProject(@NotNull Project project);

  public abstract void syncProject(@NotNull Project project);

  public abstract void addDependency(@NotNull Module module, @NotNull String dependency);

  /**
   * Merge new dependencies into a (potentially existing) build file.
   * @param dependencies new dependencies.
   * @param destinationContents original content of the build file.
   * @return new content of the build file.
   */
  public abstract String mergeBuildFiles(@NotNull String dependencies,
                                         @NotNull String destinationContents,
                                         @NotNull Project project,
                                         @Nullable String supportLibVersionFilter);

  public abstract List<AndroidSourceSet> getSourceSets(@NotNull AndroidFacet facet, @Nullable VirtualFile targetDirectory);
}
