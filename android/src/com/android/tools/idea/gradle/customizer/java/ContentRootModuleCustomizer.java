/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaJavaProject> {
  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel model, @NotNull IdeaJavaProject javaProject) {
    List<ContentEntry> allEntries = Lists.newArrayList();
    for (IdeaContentRoot contentRoot : javaProject.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirectory();
      ContentEntry contentEntry = model.addContentEntry(FilePaths.pathToIdeaUrl(rootDirPath));
      allEntries.add(contentEntry);
    }
    return allEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull Module module,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull IdeaJavaProject javaProject,
                                     @NotNull List<RootSourceFolder> orphans) {
    boolean isTopLevelJavaModule = Projects.isGradleProjectModule(module);

    File buildFolderPath = javaProject.getBuildFolderPath();
    boolean buildFolderUnexcluded = buildFolderPath == null;

    for (IdeaContentRoot contentRoot : javaProject.getContentRoots()) {
      if (contentRoot == null) {
        continue;
      }
      addSourceFolders(contentEntries, contentRoot.getSourceDirectories(), JavaSourceRootType.SOURCE, orphans);
      addSourceFolders(contentEntries, contentRoot.getTestDirectories(), JavaSourceRootType.TEST_SOURCE, orphans);

      if (contentRoot instanceof ExtIdeaContentRoot) {
        ExtIdeaContentRoot extContentRoot = (ExtIdeaContentRoot)contentRoot;
        addSourceFolders(contentEntries, extContentRoot.getResourceDirectories(), JavaResourceRootType.RESOURCE, orphans);
        addSourceFolders(contentEntries, extContentRoot.getTestResourceDirectories(), JavaResourceRootType.TEST_RESOURCE, orphans);
      }

      for (File excluded : contentRoot.getExcludeDirectories()) {
        if (excluded != null) {
          ContentEntry contentEntry = findParentContentEntry(contentEntries, excluded);
          if (contentEntry != null) {
            if (isTopLevelJavaModule && !buildFolderUnexcluded) {
              // We need to "undo" the implicit exclusion of "build" folder for top-level module.
              if (FileUtil.filesEqual(excluded, buildFolderPath)) {
                buildFolderUnexcluded = true;
                continue;
              }
            }
            addExcludedFolder(contentEntry, excluded);
          }
        }
      }
    }
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntries,
                                @Nullable Set<? extends IdeaSourceDirectory> sourceFolders,
                                @NotNull JpsModuleSourceRootType type,
                                @NotNull List<RootSourceFolder> orphans) {
    if (sourceFolders == null) {
      return;
    }
    for (IdeaSourceDirectory dir : sourceFolders) {
      File path = dir.getDirectory();
      addSourceFolder(contentEntries, path, type, false, orphans);
    }
  }
}
