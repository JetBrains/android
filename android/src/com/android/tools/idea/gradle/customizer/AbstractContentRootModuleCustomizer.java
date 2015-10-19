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

import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.*;

public abstract class AbstractContentRootModuleCustomizer<T> implements ModuleCustomizer<T> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable T externalProjectModel) {
    if (externalProjectModel == null) {
      return;
    }

    final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    for (ContentEntry contentEntry : moduleModel.getContentEntries()) {
      moduleModel.removeContentEntry(contentEntry);
    }

    Collection<ContentEntry> contentEntries = findOrCreateContentEntries(moduleModel, externalProjectModel);
    List<RootSourceFolder> orphans = Lists.newArrayList();
    setUpContentEntries(moduleModel, contentEntries, externalProjectModel, orphans);

    for (RootSourceFolder orphan : orphans) {
      File path = orphan.getPath();
      ContentEntry contentEntry = moduleModel.addContentEntry(pathToIdeaUrl(path));
      addSourceFolder(contentEntry, path, orphan.getType(), orphan.isGenerated());
    }
  }

  @NotNull
  protected abstract Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                                         @NotNull T externalProjectModel);

  protected abstract void setUpContentEntries(@NotNull ModifiableRootModel ideaModuleModel,
                                              @NotNull Collection<ContentEntry> contentEntries,
                                              @NotNull T externalProjectModel,
                                              @NotNull List<RootSourceFolder> orphans);

  protected void addSourceFolder(@NotNull Collection<ContentEntry> contentEntries,
                                 @NotNull File folderPath,
                                 @NotNull JpsModuleSourceRootType type,
                                 boolean generated,
                                 @NotNull List<RootSourceFolder> orphans) {
    ContentEntry parent = findParentContentEntry(folderPath, contentEntries);
    if (parent == null) {
      orphans.add(new RootSourceFolder(folderPath, type, generated));
      return;
    }

    addSourceFolder(parent, folderPath, type, generated);
  }

  private static void addSourceFolder(@NotNull ContentEntry contentEntry,
                                      @NotNull File folderPath,
                                      @NotNull JpsModuleSourceRootType type,
                                      boolean generated) {
    String url = pathToIdeaUrl(folderPath);
    SourceFolder sourceFolder = contentEntry.addSourceFolder(url, type);

    if (generated) {
      JpsModuleSourceRoot sourceRoot = sourceFolder.getJpsElement();
      JpsElement properties = sourceRoot.getProperties();
      if (properties instanceof JavaSourceRootProperties) {
        ((JavaSourceRootProperties)properties).setForGeneratedSources(true);
      }
    }
  }

  protected boolean addExcludedFolder(@NotNull ContentEntry contentEntry, @NotNull File dirPath) {
    if (!isPathInContentEntry(dirPath, contentEntry)) {
      return false;
    }
    contentEntry.addExcludeFolder(pathToIdeaUrl(dirPath));
    return true;
  }

  protected static class RootSourceFolder {
    @NotNull private final File myPath;
    @NotNull private final JpsModuleSourceRootType myType;
    private final boolean myGenerated;

    protected RootSourceFolder(@NotNull File path, @NotNull JpsModuleSourceRootType type, boolean generated) {
      myPath = path;
      myType = type;
      myGenerated = generated;
    }

    @NotNull
    protected File getPath() {
      return myPath;
    }

    @NotNull
    protected JpsModuleSourceRootType getType() {
      return myType;
    }

    protected boolean isGenerated() {
      return myGenerated;
    }
  }
}
