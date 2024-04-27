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
package com.android.tools.idea.gradle.roots;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.BUILD_DIR_DEFAULT_NAME;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.tools.idea.gradle.project.Info;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Indicates that source files under Gradle's "build" folder are "generated source files."
 */
public class AndroidGeneratedSourcesFilter extends GeneratedSourcesFilter {
  // See: https://code.google.com/p/android/issues/detail?id=121156
  // Alternative could be marking the entire build folder as generated source folder, but this have many side affects, e.g. a broken source
  // file (e.g. have some syntax error / warning) under build folder that is not actually used in the project would be marked as red too and
  // the user might be asked to fix it.

  @Override
  public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
    Info projectInfo = Info.getInstance(project);
    GradleAndroidModel androidModel = GradleProjectSystemUtil.findAndroidModelInModule(project, file);
    return isGeneratedSource(file, project, projectInfo, androidModel);
  }

  @VisibleForTesting
  public static boolean isGeneratedSource(@NotNull VirtualFile file,
                                          @NotNull Project project,
                                          Info projectInfo, GradleAndroidModel androidModel) {
    if (androidModel != null) {
      return isAncestor(androidModel.getAndroidProject().getBuildFolder(), virtualToIoFile(file), false);
    }

    // Gradle projects also sometimes create a "build" folder at the top level (where there is no AndroidFacet module). Unfortunately, this
    // folder is not available in the Gradle project model so we have to look for it by hardcoded name.
    VirtualFile rootFolder = project.getBaseDir();
    if (rootFolder == null) {
      return false;
    }

    VirtualFile buildFolder = rootFolder.findChild(BUILD_DIR_DEFAULT_NAME);
    boolean isBuiltWithGradle = projectInfo.isBuildWithGradle();
    return buildFolder != null && isBuiltWithGradle && isAncestor(buildFolder, file, false);
  }
}
