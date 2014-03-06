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
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.model.ExtIdeaContentRoot;

import java.io.File;

public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<JavaModel> {
  @Override
  @Nullable
  protected ContentEntry findOrCreateContentEntry(@NotNull ModifiableRootModel rootModel, @NotNull JavaModel model) {
    IdeaContentRoot contentRoot = model.getContentRoot();
    if (contentRoot == null || contentRoot.getRootDirectory() == null) {
      return null;
    }
    File rootDirPath = contentRoot.getRootDirectory();
    ContentEntry[] contentEntries = rootModel.getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile contentEntryFile = contentEntry.getFile();
      if (contentEntryFile == null) {
        continue;
      }
      String path = FileUtil.toSystemDependentName(contentEntryFile.getPath());
      if (FileUtil.pathsEqual(rootDirPath.getPath(), path)) {
        return contentEntry;
      }
    }
    return rootModel.addContentEntry(pathToUrl(rootDirPath));
  }

  @Override
  protected void setUpContentEntry(@NotNull ContentEntry contentEntry, @NotNull JavaModel model) {
    IdeaContentRoot contentRoot = model.getContentRoot();
    // We are here because JavaModel has an IdeaContentRoot.
    assert contentRoot != null;
    addSourceFolders(contentEntry, JavaSourceRootType.SOURCE, contentRoot.getSourceDirectories());
    addSourceFolders(contentEntry, JavaSourceRootType.TEST_SOURCE, contentRoot.getTestDirectories());

    if (contentRoot instanceof ExtIdeaContentRoot) {
      ExtIdeaContentRoot extContentRoot = (ExtIdeaContentRoot)contentRoot;
      addSourceFolders(contentEntry, JavaResourceRootType.RESOURCE, extContentRoot.getResourceDirectories());
      addSourceFolders(contentEntry, JavaResourceRootType.TEST_RESOURCE, extContentRoot.getTestResourceDirectories());
    }

    for (File excluded : contentRoot.getExcludeDirectories()) {
      if (excluded != null) {
        addExcludedFolder(contentEntry, excluded);
      }
    }
  }

  private void addSourceFolders(@NotNull ContentEntry contentEntry,
                                @NotNull JpsModuleSourceRootType sourceType,
                                @Nullable DomainObjectSet<? extends IdeaSourceDirectory> sourceDirectories) {
    if (sourceDirectories == null) {
      return;
    }
    for (IdeaSourceDirectory dir : sourceDirectories) {
      File path = dir.getDirectory();
      addSourceFolder(contentEntry, sourceType, path, false);
    }
  }
}
