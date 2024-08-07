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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

class WorkspaceFileFinderImpl implements SyncListener, WorkspaceFileFinder {

  private final WorkspaceRoot root;
  private volatile ImportRoots importRoots;

  WorkspaceFileFinderImpl(WorkspaceRoot root, ImportRoots importRoots) {
    this.root = root;
    this.importRoots = importRoots;
  }

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {
    importRoots =
        ImportRoots.builder(
                WorkspaceRoot.fromProjectSafe(project), Blaze.getBuildSystemName(project))
            .add(projectViewSet)
            .build();
  }

  @Override
  public boolean isInProject(File file) {
    WorkspacePath path = root.workspacePathForSafe(file);
    return path != null && importRoots.containsWorkspacePath(path);
  }

  static class Provider implements WorkspaceFileFinder.Provider {

    private final Project project;

    public Provider(Project project) {
      this.project = project;
    }

    @Nullable
    @Override
    public WorkspaceFileFinder getWorkspaceFileFinder() {
      ImportRoots importRoots = ImportRoots.forProjectSafe(project);
      if (importRoots == null) {
        return null;
      }
      return new WorkspaceFileFinderImpl(WorkspaceRoot.fromProject(project), importRoots);
    }
  }
}
