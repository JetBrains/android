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
package com.google.idea.blaze.base;

import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.testing.ServiceHelper;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Provides a {@link ProjectViewSet} for integration tests. */
public class MockProjectViewManager extends ProjectViewManager {

  private ProjectViewSet projectViewSet = ProjectViewSet.EMPTY;

  public MockProjectViewManager() {}

  public MockProjectViewManager(Project project) {
    ServiceHelper.registerProjectService(project, ProjectViewManager.class, this, project);
  }

  public void setProjectView(ProjectViewSet projectView) {
    projectViewSet = projectView;
  }

  @Nullable
  @Override
  public ProjectViewSet getProjectViewSet() {
    return projectViewSet;
  }

  @Nullable
  @Override
  public ProjectViewSet reloadProjectView(BlazeContext context) {
    return getProjectViewSet();
  }

  @Override
  public ProjectViewSet reloadProjectView(
      BlazeContext context, WorkspacePathResolver workspacePathResolver) {
    return getProjectViewSet();
  }
}
