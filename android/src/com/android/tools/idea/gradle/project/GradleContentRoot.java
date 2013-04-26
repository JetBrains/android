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

import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * Configures a module's content root from an {@link IdeaContentRoot}.
 */
final class GradleContentRoot {
  private GradleContentRoot() {
  }

  static void storePaths(@NotNull IdeaContentRoot from, @NotNull ContentRootData to) {
    storePaths(to, ExternalSystemSourceType.SOURCE, from.getSourceDirectories());
    storePaths(to, ExternalSystemSourceType.TEST, from.getTestDirectories());
    Set<File> excluded = from.getExcludeDirectories();
    if (excluded != null) {
      for (File f : excluded) {
        to.storePath(ExternalSystemSourceType.EXCLUDED, f.getAbsolutePath());
      }
    }
  }

  private static void storePaths(@NotNull ContentRootData contentRootData,
                                 @NotNull ExternalSystemSourceType sourceType,
                                 @Nullable Iterable<? extends IdeaSourceDirectory> dirs) {
    if (dirs != null) {
      for (IdeaSourceDirectory dir : dirs) {
        contentRootData.storePath(sourceType, dir.getDirectory().getAbsolutePath());
      }
    }
  }
}
