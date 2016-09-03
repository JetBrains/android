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
package com.android.tools.idea.gradle.project.sync.setup.module.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntries;
import com.intellij.openapi.module.Module;
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

class JavaContentEntries extends ContentEntries {
  @NotNull private final JavaProject myJavaProject;

  @NotNull
  static JavaContentEntries findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel,
                                                       @NotNull JavaProject javaProject) {
    List<ContentEntry> entries = new ArrayList<>();
    for (JavaModuleContentRoot contentRoot : javaProject.getContentRoots()) {
      File rootDirPath = contentRoot.getRootDirPath();
      ContentEntry contentEntry = rootModel.addContentEntry(pathToIdeaUrl(rootDirPath));
      entries.add(contentEntry);
    }
    return new JavaContentEntries(javaProject, rootModel, entries);
  }

  private JavaContentEntries(@NotNull JavaProject javaProject,
                             @NotNull ModifiableRootModel rootModel,
                             @NotNull Collection<ContentEntry> values) {
    super(rootModel, values);
    myJavaProject = javaProject;
  }

  void setUpContentEntries(@NotNull Module module) {
    boolean isTopLevelJavaModule = isGradleProjectModule(module);

    File buildFolderPath = myJavaProject.getBuildFolderPath();
    boolean buildFolderExcluded = buildFolderPath != null;

    for (JavaModuleContentRoot contentRoot : myJavaProject.getContentRoots()) {
      if (contentRoot == null) {
        continue;
      }
      addSourceFolders(contentRoot.getSourceDirPaths(), SOURCE, false);
      addSourceFolders(contentRoot.getGenSourceDirPaths(), SOURCE, true);
      addSourceFolders(contentRoot.getResourceDirPaths(), RESOURCE, false);
      addSourceFolders(contentRoot.getTestDirPaths(), TEST_SOURCE, false);
      addSourceFolders(contentRoot.getGenTestDirPaths(), TEST_SOURCE, true);
      addSourceFolders(contentRoot.getTestResourceDirPaths(), TEST_RESOURCE, false);

      for (File excluded : contentRoot.getExcludeDirPaths()) {
        ContentEntry contentEntry = findParentContentEntry(excluded, getValues());
        if (contentEntry != null) {
          if (isTopLevelJavaModule && buildFolderExcluded) {
            // We need to "undo" the implicit exclusion of "build" folder for top-level module.
            if (filesEqual(excluded, buildFolderPath)) {
              buildFolderExcluded = true;
              continue;
            }
          }
          addExcludedFolder(contentEntry, excluded);
        }
      }
    }

    addOrphans();
  }

  private void addSourceFolders(@NotNull Collection<File> folderPaths, @NotNull JpsModuleSourceRootType type, boolean generated) {
    for (File path : folderPaths) {
      addSourceFolder(path, type, generated);
    }
  }
}
