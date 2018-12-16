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

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleSettingsFile;

/**
 * Cache to store a mapping between file paths and their respective {@link GradleDslFileCache} objects, its main purpose it to
 * prevent the parsing of a file more than once. In large projects without caching the parsed file we can end up parsing the same
 * file hundreds of times.
 */
public class GradleDslFileCache {
  @NotNull private Project myProject;
  @NotNull private Map<String, GradleDslFile> myParsedBuildFiles = new HashMap<>();

  public GradleDslFileCache(@NotNull Project project) {
    myProject = project;
  }

  public void clearAllFiles() {
    myParsedBuildFiles.clear();
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file,
                                              @NotNull String name,
                                              @NotNull BuildModelContext context,
                                              boolean isApplied) {
    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      dslFile = GradleBuildModelImpl.parseBuildFile(file, myProject, name, context, isApplied);
      myParsedBuildFiles.put(file.getUrl(), dslFile);
    }
    else if (!(dslFile instanceof GradleBuildFile)) {
      throw new IllegalStateException("Found wrong type for build file in cache!");
    }

    return (GradleBuildFile)dslFile;
  }

  public void putBuildFile(@NotNull String name, @NotNull GradleDslFile buildFile) {
    myParsedBuildFiles.put(name, buildFile);
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

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile, @NotNull BuildModelContext context) {
    GradleDslFile dslFile = myParsedBuildFiles.get(settingsFile.getUrl());
    if (dslFile == null) {
      dslFile = new GradleSettingsFile(settingsFile, myProject, "settings", context);
      dslFile.parse();
      myParsedBuildFiles.put(settingsFile.getUrl(), dslFile);
    }
    else if (!(dslFile instanceof GradleSettingsFile)) {
      throw new IllegalStateException("Found wrong type for settings file in cache!");
    }
    return (GradleSettingsFile)dslFile;
  }

  @NotNull
  public List<GradleDslFile> getAllFiles() {
    return new ArrayList<>(myParsedBuildFiles.values());
  }
}
