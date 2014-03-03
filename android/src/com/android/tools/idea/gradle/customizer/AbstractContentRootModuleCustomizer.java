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
package com.android.tools.idea.gradle.customizer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;

public abstract class AbstractContentRootModuleCustomizer<T> implements ModuleCustomizer<T> {
  private static final Logger LOG = Logger.getInstance(AbstractContentRootModuleCustomizer.class);

  @NonNls public static final String BUILD_DIR = "build";

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable T model) {
    if (model == null) {
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();

    try {
      for (ContentEntry contentEntry : rootModel.getContentEntries()) {
        rootModel.removeContentEntry(contentEntry);
      }

      Collection<ContentEntry> contentEntries = findOrCreateContentEntries(rootModel, model);
      setUpContentEntries(contentEntries, model);
    }
    finally {
      rootModel.commit();
    }
  }

  @NotNull
  protected abstract Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel, @NotNull T model);

  protected abstract void setUpContentEntries(@NotNull Collection<ContentEntry> contentEntries, @NotNull T model);

  protected void addSourceFolder(@NotNull Collection<ContentEntry> contentEntries,
                                 @NotNull JpsModuleSourceRootType sourceRootType,
                                 @NotNull File dirPath,
                                 boolean isGenerated) {
    ContentEntry parent = findParentContentEntry(contentEntries, dirPath);
    if (parent == null) {
      return;
    }

    String url = pathToUrl(dirPath);

    SourceFolder sourceFolder = parent.addSourceFolder(url, sourceRootType);

    if (isGenerated) {
      JpsModuleSourceRoot sourceRoot = sourceFolder.getJpsElement();
      JpsElement properties = sourceRoot.getProperties();
      if (properties instanceof JavaSourceRootProperties) {
        ((JavaSourceRootProperties)properties).setForGeneratedSources(true);
      }
    }
  }

  protected void addExcludedFolder(@NotNull ContentEntry contentEntry, @NotNull File dirPath) {
    String url = pathToUrl(dirPath);
    contentEntry.addExcludeFolder(url);
  }

  @Nullable
  protected ContentEntry findParentContentEntry(@NotNull Collection<ContentEntry> contentEntries, @NotNull File dirPath) {
    for (ContentEntry contentEntry : contentEntries) {
      if (isPathInContentEntry(dirPath, contentEntry)) {
        return contentEntry;
      }
    }
    LOG.info(String.format("Failed to find content entry for file '%1$s'", dirPath.getPath()));
    return null;
  }

  protected boolean isPathInContentEntry(@NotNull File path, @NotNull ContentEntry contentEntry) {
    VirtualFile rootFile = contentEntry.getFile();
    if (rootFile == null) {
      return false;
    }
    File rootFilePath = VfsUtilCore.virtualToIoFile(rootFile);
    return FileUtil.isAncestor(rootFilePath, path, false);
  }

  @NotNull
  protected String pathToUrl(@NotNull File dirPath) {
    String path = FileUtil.toSystemIndependentName(dirPath.getPath());
    return VfsUtilCore.pathToUrl(path);
  }
}
