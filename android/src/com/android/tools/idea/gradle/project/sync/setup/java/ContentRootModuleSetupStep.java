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
package com.android.tools.idea.gradle.project.sync.setup.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.project.sync.JavaModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.ContentEntries;
import com.android.tools.idea.gradle.project.sync.setup.RootSourceFolder;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

public class ContentRootModuleSetupStep extends JavaModuleSetupStep {
  private final ContentEntries myContentEntries = new ContentEntries();

  @Override
  public void setUpModule(@NotNull Module module,
                          @NotNull JavaProject javaProject,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull SyncAction.ModuleModels gradleModels,
                          @NotNull ProgressIndicator indicator) {
    ModifiableRootModel rootModel = ideModelsProvider.getModifiableRootModel(module);
    myContentEntries.removeExisting(rootModel);

    Collection<ContentEntry> contentEntries = findOrCreateContentEntries(rootModel, javaProject);
    List<RootSourceFolder> orphans = new ArrayList<>();

    setUpContentEntries(module, contentEntries, javaProject, orphans);
  }

  @NotNull
  private static Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel,
                                                                     @NotNull JavaProject javaProject) {
    List<ContentEntry> entries = new ArrayList<>();
    for (JavaModuleContentRoot contentRoot : javaProject.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirPath();
      ContentEntry contentEntry = rootModel.addContentEntry(pathToIdeaUrl(rootDirPath));
      entries.add(contentEntry);
    }
    return entries;
  }

  private void setUpContentEntries(@NotNull Module module,
                                   @NotNull Collection<ContentEntry> contentEntries,
                                   @NotNull JavaProject javaProject,
                                   @NotNull List<RootSourceFolder> orphans) {
    boolean isTopLevelJavaModule = isGradleProjectModule(module);

    File buildFolderPath = javaProject.getBuildFolderPath();
    boolean buildFolderExcluded = buildFolderPath != null;

    for (JavaModuleContentRoot contentRoot : javaProject.getContentRoots()) {
      if (contentRoot == null) {
        continue;
      }
      addSourceFolders(contentEntries, contentRoot.getSourceDirPaths(), SOURCE, orphans, false);
      addSourceFolders(contentEntries, contentRoot.getGenSourceDirPaths(), SOURCE, orphans, true);
      addSourceFolders(contentEntries, contentRoot.getResourceDirPaths(), RESOURCE, orphans, false);
      addSourceFolders(contentEntries, contentRoot.getTestDirPaths(), TEST_SOURCE, orphans, false);
      addSourceFolders(contentEntries, contentRoot.getGenTestDirPaths(), TEST_SOURCE, orphans, true);
      addSourceFolders(contentEntries, contentRoot.getTestResourceDirPaths(), TEST_RESOURCE, orphans, false);

      for (File excluded : contentRoot.getExcludeDirPaths()) {
        ContentEntry contentEntry = findParentContentEntry(excluded, contentEntries);
        if (contentEntry != null) {
          if (isTopLevelJavaModule && buildFolderExcluded) {
            // We need to "undo" the implicit exclusion of "build" folder for top-level module.
            if (filesEqual(excluded, buildFolderPath)) {
              buildFolderExcluded = true;
              continue;
            }
          }
          myContentEntries.addExcludedFolder(contentEntry, excluded);
        }
      }
    }
  }

  private void addSourceFolders(@NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> sourceDirPaths,
                                @NotNull JpsModuleSourceRootType type,
                                @NotNull List<RootSourceFolder> orphans,
                                boolean generated) {
    for (File path : sourceDirPaths) {
      myContentEntries.addSourceFolder(contentEntries, path, type, orphans, generated);
    }
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Source folder(s) setup";
  }
}
