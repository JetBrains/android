/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static java.util.Arrays.asList;

import com.android.tools.layoutlib.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Register a listener to PROJECT_SYSTEM_SYNC_TOPIC, which notifies sync finished when gradle sync and
 * source generation (if requested) are both completed. When sync finishes, refresh file system with all
 * root urls in library table.
 */

public class FileSystemUpdater {
  @NotNull private final Project myProject;

  @NotNull
  public static FileSystemUpdater getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FileSystemUpdater.class);
  }

  private FileSystemUpdater(@NotNull Project project) {
    myProject = project;
    myProject.getMessageBus().connect(myProject).subscribe(PROJECT_SYSTEM_SYNC_TOPIC,
                                                           result -> ApplicationManager.getApplication()
                                                             .executeOnPooledThread(this::updateLibraryRootsInFileSystem));
  }

  private void updateLibraryRootsInFileSystem() {
    // Collect all expected root urls.
    List<String> expectedUrls = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      expectedUrls.addAll(asList(rootManager.getContentRootUrls()));
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach(entry -> {
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          expectedUrls.addAll(asList(entry.getUrls(type)));
        }
        return true;
      });
    }

    // Ensure virtual file system is updated with the expected root urls.
    for (String url : expectedUrls) {
      VirtualFileManager manager = VirtualFileManager.getInstance();
      if (manager.findFileByUrl(url) == null) {
        manager.refreshAndFindFileByUrl(url);
      }
    }
  }
}
