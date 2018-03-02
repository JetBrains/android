/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;

public class GradleDslFileCache {
  @NotNull Project myProject;
  @NotNull Map<String, GradleDslFile> myParsedBuildFiles = new HashMap<>();

  public GradleDslFileCache(@NotNull Project project) {
    myProject = project;
  }

  public void clearAllFiles() {
    myParsedBuildFiles.clear();
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file) {
    return getOrCreateBuildFile(file, file.getName());
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file, @NotNull String name) {
    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      dslFile = GradleBuildModelImpl.parseBuildFile(file, myProject, name, this);
      myParsedBuildFiles.put(file.getUrl(), dslFile);
    } else if (!(dslFile instanceof GradleBuildFile)) {
      throw new IllegalStateException("Found wrong type for build file in cache!");
    }

    return (GradleBuildFile)dslFile;
  }

  @Nullable
  public GradleSettingsFile getSettingsFile(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    if (file == null) {
      return null;
    }

    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile != null && !(dslFile instanceof GradleSettingsFile)) {
      throw new IllegalStateException("Found wrong type for settings file in cache!");
    }
    return (GradleSettingsFile)dslFile;
  }

  @Nullable
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    if (file == null) {
      return null;
    }

    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      dslFile = new GradleSettingsFile(file, myProject, "settings", this);
      dslFile.parse();
      myParsedBuildFiles.put(file.getUrl(), dslFile);
    } else if (!(dslFile instanceof GradleSettingsFile)) {
      throw new IllegalStateException("Found wrong type for settings file in cache!");
    }
    return (GradleSettingsFile)dslFile;
  }
}
