/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

class ProjectViewAutoSyncProvider implements AutoSyncProvider {

  @Override
  public boolean isSyncSensitiveFile(Project project, VirtualFile file) {
    return isProjectViewFileForProject(project, file);
  }

  private static boolean isProjectViewFileForProject(Project project, VirtualFile file) {
    if (!ProjectViewStorageManager.isProjectViewFile(file.getPath())) {
      return false;
    }
    // check that it's actually associated with this project
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return false;
    }
    return projectViewSet
        .getProjectViewFiles()
        .stream()
        .anyMatch(
            f -> f.projectViewFile != null && file.getPath().equals(f.projectViewFile.getPath()));
  }

  @Nullable
  @Override
  public BlazeSyncParams getAutoSyncParamsForFile(Project project, VirtualFile modifiedFile) {
    if (!AutoSyncSettings.getInstance().autoSyncOnBuildChanges
        || !isSyncSensitiveFile(project, modifiedFile)) {
      return null;
    }
    // run a full incremental sync in response to .bazelproject changes
    return BlazeSyncParams.builder()
        .setTitle(AUTO_SYNC_TITLE)
        .setSyncMode(SyncMode.INCREMENTAL)
        .setSyncOrigin(AUTO_SYNC_REASON + ".ProjectViewAutoSyncProvider")
        .setAddProjectViewTargets(true)
        .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
        .setBackgroundSync(true)
        .build();
  }
}
