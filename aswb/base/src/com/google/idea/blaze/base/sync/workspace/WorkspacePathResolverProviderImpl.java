/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.workspace;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import javax.annotation.Nullable;

/** Provides a WorkspacePathResolver. */
public class WorkspacePathResolverProviderImpl implements WorkspacePathResolverProvider {

  private final Project project;

  private volatile WorkspacePathResolver tempOverride = null;

  public WorkspacePathResolverProviderImpl(Project project) {
    this.project = project;
  }

  @Override
  public void setTemporaryOverride(WorkspacePathResolver resolver, Disposable parentDisposable) {
    tempOverride = resolver;
    Disposer.register(parentDisposable, () -> tempOverride = null);
  }

  @Nullable
  @Override
  public WorkspacePathResolver getPathResolver() {
    WorkspacePathResolver tempOverride = this.tempOverride;
    if (tempOverride != null) {
      return tempOverride;
    }
    if (Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC) {
      BlazeProjectData projectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData != null) {
        return projectData.getWorkspacePathResolver();
      }
    }
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    if (root != null) {
      // fallback to a default path resolver until we get more information (e.g. from the next sync)
      return new WorkspacePathResolverImpl(root);
    }
    return null;
  }
}
