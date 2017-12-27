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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.ContentEntries.findParentContentEntry;
import static com.android.tools.idea.gradle.util.ContentEntries.isPathInContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;

public abstract class ContentEntriesSetup {
  @NotNull private final ModifiableRootModel myModuleModel;
  @NotNull private final List<RootSourceFolder> myOrphans = new ArrayList<>();

  protected ContentEntriesSetup(@NotNull ModifiableRootModel moduleModel) {
    myModuleModel = moduleModel;
  }

  public static void removeExistingContentEntries(@NotNull ModifiableRootModel rootModel) {
    for (ContentEntry contentEntry : rootModel.getContentEntries()) {
      rootModel.removeContentEntry(contentEntry);
    }
  }

  public abstract void execute(@NotNull List<ContentEntry> contentEntries);

  protected void addSourceFolder(@NotNull File folderPath,
                                 @NotNull Collection<ContentEntry> contentEntries,
                                 @NotNull JpsModuleSourceRootType type,
                                 boolean generated) {
    ContentEntry parent = findParentContentEntry(folderPath, contentEntries.stream());
    if (parent == null) {
      myOrphans.add(new RootSourceFolder(folderPath, type, generated));
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

  protected boolean addExcludedFolder(@NotNull ContentEntry contentEntry, @NotNull File folderPath) {
    if (!isPathInContentEntry(folderPath, contentEntry)) {
      return false;
    }
    contentEntry.addExcludeFolder(pathToIdeaUrl(folderPath));
    return true;
  }

  protected void addOrphans() {
    for (RootSourceFolder orphan : myOrphans) {
      File path = orphan.getPath();
      ContentEntry contentEntry = myModuleModel.addContentEntry(pathToIdeaUrl(path));
      addSourceFolder(contentEntry, path, orphan.getType(), orphan.isGenerated());
    }
  }

  @NotNull
  protected Module getModule() {
    return myModuleModel.getModule();
  }
}
