/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.ContentEntries.findParentContentEntry;
import static com.android.tools.idea.gradle.util.Projects.isGradleProjectModule;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

class JavaContentEntriesSetup extends ContentEntriesSetup {
  @NotNull private final JavaModuleModel myJavaModuleModel;

  JavaContentEntriesSetup(@NotNull JavaModuleModel javaModuleModel, @NotNull ModifiableRootModel moduleModel) {
    super(moduleModel);
    myJavaModuleModel = javaModuleModel;
  }

  @Override
  public void execute(@NotNull List<ContentEntry> contentEntries) {
    boolean isTopLevelJavaModule = isGradleProjectModule(getModule());

    File buildFolderPath = myJavaModuleModel.getBuildFolderPath();
    boolean buildFolderExcluded = buildFolderPath != null;

    for (JavaModuleContentRoot contentRoot : myJavaModuleModel.getContentRoots()) {
      if (contentRoot == null) {
        continue;
      }
      addSourceFolders(contentRoot.getSourceDirPaths(), contentEntries, SOURCE, false);
      addSourceFolders(contentRoot.getGenSourceDirPaths(), contentEntries, SOURCE, true);
      addSourceFolders(contentRoot.getResourceDirPaths(), contentEntries, RESOURCE, false);
      addSourceFolders(contentRoot.getTestDirPaths(), contentEntries, TEST_SOURCE, false);
      addSourceFolders(contentRoot.getGenTestDirPaths(), contentEntries, TEST_SOURCE, true);
      addSourceFolders(contentRoot.getTestResourceDirPaths(), contentEntries, TEST_RESOURCE, false);

      for (File excluded : contentRoot.getExcludeDirPaths()) {
        ContentEntry contentEntry = findParentContentEntry(excluded, contentEntries.stream());
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

  private void addSourceFolders(@NotNull Collection<File> folderPaths,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated) {
    for (File path : folderPaths) {
      addSourceFolder(path, contentEntries, type, generated);
    }
  }
}
