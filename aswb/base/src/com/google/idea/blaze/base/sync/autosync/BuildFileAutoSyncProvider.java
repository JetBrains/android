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

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

class BuildFileAutoSyncProvider implements AutoSyncProvider {

  @Override
  public boolean isSyncSensitiveFile(Project project, VirtualFile file) {
    // we'll just assume any BUILD file being modified is in the project
    return isBuildFile(project, file);
  }

  private static boolean isBuildFile(Project project, VirtualFile file) {
    return Blaze.getBuildSystemProvider(project).isBuildFile(file.getName());
  }

  @Nullable
  private static WorkspacePath getWorkspacePath(Project project, VirtualFile file) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null
        ? projectData.getWorkspacePathResolver().getWorkspacePath(new File(file.getPath()))
        : null;
  }

  @Nullable
  @Override
  public BlazeSyncParams getAutoSyncParamsForFile(Project project, VirtualFile modifiedFile) {
    if (!AutoSyncSettings.getInstance().autoSyncOnBuildChanges
        || !isSyncSensitiveFile(project, modifiedFile)) {
      return null;
    }
    WorkspacePath path = getWorkspacePath(project, modifiedFile);
    if (path == null || path.getParent() == null) {
      return null;
    }
    return BlazeSyncParams.builder()
        .setTitle(AUTO_SYNC_TITLE)
        .setSyncMode(SyncMode.PARTIAL)
        .setSyncOrigin(AUTO_SYNC_REASON + ".BuildFileAutoSyncProvider")
        .addTargetExpression(TargetExpression.allFromPackageNonRecursive(path.getParent()))
        .setBackgroundSync(true)
        .build();
  }
}
