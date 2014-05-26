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

import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.facet.JavaModel;
import com.android.tools.idea.gradle.util.FilePaths;
import com.google.common.collect.Lists;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
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

public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<JavaModel> {
  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel, @NotNull JavaModel model) {
    List<ContentEntry> allEntries = Lists.newArrayList();
    for (IdeaContentRoot contentRoot : model.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirectory();
      ContentEntry contentEntry = rootModel.addContentEntry(FilePaths.pathToIdeaUrl(rootDirPath));
      allEntries.add(contentEntry);
    }
    return allEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull JavaModel model,
                                     @NotNull List<RootSourceFolder> orphans) {
    List<IdeaContentRoot> contentRoots = model.getContentRoots();
    for (IdeaContentRoot contentRoot : contentRoots) {
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
