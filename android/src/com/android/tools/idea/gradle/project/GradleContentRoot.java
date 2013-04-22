/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Set;

/**
 * Creates a module content root from an {@link IdeaContentRoot}.
 */
class GradleContentRoot {
  @NotNull private final ContentRootData myContentRootData;

  GradleContentRoot(@NotNull String rootPath) {
    myContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, rootPath);
  }

  /**
   * Adds this content root to the given IDE module.
   *
   * @param moduleInfo the given IDE module.
   */
  void addTo(@NotNull DataNode<ModuleData> moduleInfo) {
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, myContentRootData);
  }

  /**
   * Stores the paths of 'source'/'test'/'excluded' directories, according to the structure of the given {@link IdeaContentRoot}.
   *
   * @param contentRoot structure of an IDEA module content root, provided by the Gradle Tooling API.
   */
  void storePaths(@NotNull IdeaContentRoot contentRoot) {
    storePaths(ExternalSystemSourceType.SOURCE, contentRoot.getSourceDirectories());
    storePaths(ExternalSystemSourceType.TEST, contentRoot.getTestDirectories());
    Set<File> excluded = contentRoot.getExcludeDirectories();
    if (excluded != null) {
      for (File f : excluded) {
        myContentRootData.storePath(ExternalSystemSourceType.EXCLUDED, f.getAbsolutePath());
      }
    }
  }

  private void storePaths(@NotNull ExternalSystemSourceType sourceType, @Nullable Iterable<? extends IdeaSourceDirectory> dirs) {
    if (dirs != null) {
      for (IdeaSourceDirectory dir : dirs) {
        myContentRootData.storePath(sourceType, dir.getDirectory().getAbsolutePath());
      }
    }
  }
}
