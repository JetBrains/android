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

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileType;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the modification of model files in the whole project.
 */
@Service
public final class ProjectMlModelFileTracker extends SimpleModificationTracker {
  @Nullable
  private GradleVersion myGradleVersion;

  public ProjectMlModelFileTracker(@NotNull Project project) {
    myGradleVersion = getGradleVersionFromProject(project);

    project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        boolean hasModelFile = false;
        boolean needRebuildIndex = false;
        for (VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file != null && TfliteModelFileType.TFLITE_EXTENSION.equalsIgnoreCase(file.getExtension())) {
            hasModelFile = true;
            if (event instanceof VFileContentChangeEvent) {
              needRebuildIndex = true;
              break;
            }
          }
        }
        if (hasModelFile) {
          incModificationCount();
        }
        if (needRebuildIndex) {
          FileBasedIndex.getInstance().requestRebuild(MlModelFileIndex.INDEX_ID);
        }
      }
    });

    project.getMessageBus().connect(project).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, new ProjectSystemSyncManager.SyncResultListener() {
      @Override
      public void syncEnded(@NotNull ProjectSystemSyncManager.SyncResult result) {
        if (result.isSuccessful()) {
          GradleVersion gradleVersion = getGradleVersionFromProject(project);
          if (!Objects.equals(myGradleVersion, gradleVersion)) {
            Logger.getInstance(ProjectMlModelFileTracker.class).info("AGP version changed, refresh all light classes cache.");
            myGradleVersion = gradleVersion;
            incModificationCount();
          }
        }
      }
    });
  }

  @Nullable
  private static GradleVersion getGradleVersionFromProject(@NotNull Project project) {
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.findFromModel(project);
    if (androidPluginInfo != null) {
      return androidPluginInfo.getPluginVersion();
    }
    return null;
  }

  @NotNull
  public static ProjectMlModelFileTracker getInstance(@NotNull Project project) {
    return project.getService(ProjectMlModelFileTracker.class);
  }

  @VisibleForTesting
  void setGradleVersion(@Nullable GradleVersion gradleVersion) {
    myGradleVersion = gradleVersion;
  }
}
