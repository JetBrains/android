/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Consider all files under gradle's build folder as generated source files so that search results under build folder will be
 * grouped into 'Generated Source' category.
 * Details: https://code.google.com/p/android/issues/detail?id=121156
 *
 * Alternative could be marking the entire build folder as generated source folder, but this have many side affects, e.g. a broken source
 * file (e.g. have some syntax error / warning) under build folder that is not actually used in the project would be marked as red too and
 * the user might be asked to fix it.
 */
public class AndroidGeneratedSourcesFilter extends GeneratedSourcesFilter {
  @Override
  public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
    AndroidProject androidProject = Projects.getAndroidModel(file, project);
    if (androidProject != null) {
      return isAncestor(androidProject.getBuildFolder(), virtualToIoFile(file), false);
    } else {
      // Gradle projects also sometimes create a "build" folder at the top level (where there
      // is no AndroidFacet module). Unfortunately, this folder is not available in the
      // Gradle project model so we have to look for it by hardcoded name.
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir == null) return false;
      VirtualFile build = baseDir.findChild(GradleUtil.BUILD_DIR_DEFAULT_NAME);
      if (build != null && Projects.isBuildWithGradle(project)) {
        return isAncestor(build, file, false);
      }
    }
    return false;
  }
}
