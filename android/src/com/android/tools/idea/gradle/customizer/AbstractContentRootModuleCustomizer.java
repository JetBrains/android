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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;

public abstract class AbstractContentRootModuleCustomizer<T> implements ModuleCustomizer<T> {
  @NonNls public static final String BUILD_DIR = "build";

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable T model) {
    if (model == null) {
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();

    try {
      ContentEntry contentEntry = findOrCreateContentEntry(rootModel, model);
      if (contentEntry == null) {
        // This may happen only with Java libs, but very, very unlikely.
        return;
      }
      contentEntry.clearSourceFolders();
      setUpContentEntry(contentEntry, model);
    }
    finally {
      rootModel.commit();
    }
  }

  @Nullable
  protected abstract ContentEntry findOrCreateContentEntry(@NotNull ModifiableRootModel rootModel, @NotNull T model);

  protected abstract void setUpContentEntry(@NotNull ContentEntry contentEntry, @NotNull T model);

  protected void addSourceFolder(@NotNull ContentEntry contentEntry,
                                 @NotNull JpsModuleSourceRootType sourceRootType,
                                 @NotNull File dirPath,
                                 boolean isGenerated) {
    String url = pathToUrl(dirPath);

    SourceFolder sourceFolder = contentEntry.addSourceFolder(url, sourceRootType);

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

  @NotNull
  protected String pathToUrl(@NotNull File dirPath) {
    String path = FileUtil.toSystemIndependentName(dirPath.getPath());
    return VfsUtilCore.pathToUrl(path);
  }
}
