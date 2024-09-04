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
package com.google.idea.blaze.base.wizard2;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.File;
import javax.annotation.Nullable;

/** All relevant data from the 'select workspace type' step of the blaze project import wizard. */
@AutoValue
public abstract class WorkspaceTypeData {

  /** Returns the workspace root that will be created after commit. */
  public abstract WorkspaceRoot workspaceRoot();

  /**
   * The default location to put the project data directory, or null if it's not fixed relative to
   * the workspace root.
   */
  @Nullable
  public abstract File canonicalProjectDataLocation();

  /** Returns a workspace path resolver to use during wizard validation. */
  public abstract WorkspacePathResolver workspacePathResolver();

  /** Returns a root directory to use for browsing workspace paths. */
  public abstract File fileBrowserRoot();

  /** Returns the name of the workspace. Used to generate default project names. */
  public abstract String workspaceName();

  /** Returns the name of the 'branch', if applicable */
  @Nullable
  public abstract String branchName();

  public abstract BuildSystemName buildSystem();

  public static WorkspaceTypeData.Builder builder() {
    return new AutoValue_WorkspaceTypeData.Builder();
  }

  /** Builder for {@link WorkspaceTypeData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract WorkspaceTypeData.Builder setWorkspaceRoot(WorkspaceRoot root);

    public abstract WorkspaceTypeData.Builder setCanonicalProjectDataLocation(
        @Nullable File canonicalProjectDataLocation);

    public abstract WorkspaceTypeData.Builder setWorkspacePathResolver(
        WorkspacePathResolver pathResolver);

    public abstract WorkspaceTypeData.Builder setFileBrowserRoot(File fileBrowserRoot);

    public abstract WorkspaceTypeData.Builder setWorkspaceName(String workspaceName);

    public abstract WorkspaceTypeData.Builder setBranchName(@Nullable String branchName);

    public abstract WorkspaceTypeData.Builder setBuildSystem(BuildSystemName buildSystemName);

    public abstract WorkspaceTypeData build();
  }
}
