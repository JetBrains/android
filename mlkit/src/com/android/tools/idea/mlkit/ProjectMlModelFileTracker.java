/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks the modification of model files in the whole project.
 */
// TODO(b/155121511): try to remove the similar file tracker from MlkitModuleService.
@Service
public final class ProjectMlModelFileTracker extends SimpleModificationTracker {

  public ProjectMlModelFileTracker(@NotNull Project project) {
    project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null && file.getFileType() == TfliteModelFileType.INSTANCE) {
            incModificationCount();
            return;
          }
        }
      }
    });
  }

  @NotNull
  public static ProjectMlModelFileTracker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectMlModelFileTracker.class);
  }
}
