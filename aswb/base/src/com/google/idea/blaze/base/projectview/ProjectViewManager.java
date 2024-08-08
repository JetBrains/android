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
package com.google.idea.blaze.base.projectview;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Class that manages access to a project's {@link ProjectView}. */
public abstract class ProjectViewManager {

  public static ProjectViewManager getInstance(Project project) {
    return project.getService(ProjectViewManager.class);
  }

  /** Returns the current project view collection. If there is an error, returns null. */
  @Nullable
  public abstract ProjectViewSet getProjectViewSet();

  /**
   * Reloads the project view, replacing the current one only if there are no errors. Calculates a
   * VCS-aware {@link WorkspacePathResolver} if necessary.
   */
  @Nullable
  public abstract ProjectViewSet reloadProjectView(BlazeContext context);

  /** Reloads the project view, replacing the current one only if there are no errors. */
  public abstract ProjectViewSet reloadProjectView(
      BlazeContext context, WorkspacePathResolver workspacePathResolver) throws BuildException;
}
