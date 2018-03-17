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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class ProjectBuildModelImpl implements ProjectBuildModel {
  @NotNull private final BuildModelContext myBuildModelContext;
  @NotNull private final GradleBuildFile myProjectBuildFile;

  @Nullable
  public static ProjectBuildModel get(@NotNull Project project) {
    VirtualFile file = getGradleBuildFile(getBaseDirPath(project));
    return file == null ? null : new ProjectBuildModelImpl(project, file);
  }

  /**
   * @param project the project this model should be built for
   * @param file the file contain the projects main build.gradle
   */
  private ProjectBuildModelImpl(@NotNull Project project, @NotNull VirtualFile file) {
    myBuildModelContext = BuildModelContext.create(project);

    // First parse the main project build file.
    myProjectBuildFile = myBuildModelContext.getOrCreateBuildFile(file, project.getName());
  }


  @Override
  @NotNull
  public GradleBuildModel getProjectBuildModel() {
    return new GradleBuildModelImpl(myProjectBuildFile);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    if (file == null) {
      return null;
    }
    GradleBuildFile dslFile = myBuildModelContext.getOrCreateBuildFile(file);
    return new GradleBuildModelImpl(dslFile);
  }

  @Override
  @Nullable
  public GradleBuildModel getModuleBuildModel(@NotNull File modulePath) {
    VirtualFile file = getGradleBuildFile(modulePath);
    if (file == null) {
      return null;
    }
    GradleBuildFile dslFile = myBuildModelContext.getOrCreateBuildFile(file);
    return new GradleBuildModelImpl(dslFile);
  }

  @Override
  @Nullable
  public GradleSettingsModel getProjectSettingsModel() {
    GradleSettingsFile settingsFile = myBuildModelContext.getOrCreateSettingsFile(myProjectBuildFile.getProject());
    if (settingsFile == null) {
      return null;
    }
    return new GradleSettingsModelImpl(settingsFile);
  }

  @Override
  public void applyChanges() {
    runOverProjectTree(GradleDslFile::applyChanges);
  }

  @Override
  public void resetState() {
    runOverProjectTree(GradleDslFile::resetState);
  }

  @Override
  public void reparse() {
    myBuildModelContext.reset();
    runOverProjectTree(GradleDslFile::reparse);
  }

  private void runOverProjectTree(@NotNull Consumer<GradleDslFile> func) {
    // This tree structure should NEVER contain any cycles. As such we assume none exist.
    Deque<GradleDslFile> currentFiles = new ArrayDeque<>();
    currentFiles.add(myProjectBuildFile);
    GradleSettingsFile settingsFile = myBuildModelContext.getSettingsFile(myProjectBuildFile.getProject());
    if (settingsFile != null) {
      currentFiles.add(settingsFile);
    }
    while (!currentFiles.isEmpty()) {
      GradleDslFile file = currentFiles.pollLast();
      func.accept(file);
      currentFiles.addAll(file.getChildModuleDslFiles());
    }
  }
}
