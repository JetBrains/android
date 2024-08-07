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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Converts execution-root-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 *
 * <p>Files which exist both underneath the execution root and within the workspace will be resolved
 * to workspace paths.
 */
public class ExecutionRootPathResolver {

  private final ImmutableList<String> buildArtifactDirectories;
  private final File executionRoot;
  private final WorkspacePathResolver workspacePathResolver;

  public ExecutionRootPathResolver(
      BuildSystemProvider buildSystemProvider,
      WorkspaceRoot workspaceRoot,
      File executionRoot,
      WorkspacePathResolver workspacePathResolver) {
    this.buildArtifactDirectories = buildArtifactDirectories(buildSystemProvider, workspaceRoot);
    this.executionRoot = executionRoot;
    this.workspacePathResolver = workspacePathResolver;
  }

  @Nullable
  public static ExecutionRootPathResolver fromProject(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return new ExecutionRootPathResolver(
        Blaze.getBuildSystemProvider(project),
        WorkspaceRoot.fromProject(project),
        projectData.getBlazeInfo().getExecutionRoot(),
        projectData.getWorkspacePathResolver());
  }

  private static ImmutableList<String> buildArtifactDirectories(
      BuildSystemProvider buildSystemProvider, WorkspaceRoot workspaceRoot) {
    return buildSystemProvider.buildArtifactDirectories(workspaceRoot);
  }

  public File resolveExecutionRootPath(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return path.getAbsoluteOrRelativeFile();
    }
    if (isInWorkspace(path)) {
      return workspacePathResolver.resolveToFile(path.getAbsoluteOrRelativeFile().getPath());
    }
    return path.getFileRootedAt(executionRoot);
  }

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the
   * given execution-root-relative path. If the file does not exist inside the workspace (e.g. for
   * blaze output files or external workspace files), returns the path rooted in the execution root.
   */
  public ImmutableList<File> resolveToIncludeDirectories(ExecutionRootPath path) {
    if (path.isAbsolute()) {
      return ImmutableList.of(path.getAbsoluteOrRelativeFile());
    }
    if (isInWorkspace(path)) {
      WorkspacePath workspacePath =
          WorkspacePath.createIfValid(path.getAbsoluteOrRelativeFile().getPath());
      if (workspacePath != null) {
        return workspacePathResolver.resolveToIncludeDirectories(workspacePath);
      } else {
        return ImmutableList.of();
      }
    }
    return ImmutableList.of(path.getFileRootedAt(executionRoot));
  }

  public File getExecutionRoot() {
    return executionRoot;
  }

  private boolean isInWorkspace(ExecutionRootPath path) {
    String firstPathComponent = getFirstPathComponent(path.getAbsoluteOrRelativeFile().getPath());
    return !buildArtifactDirectories.contains(firstPathComponent)
        && !isExternalWorkspacePath(firstPathComponent);
  }

  private static String getFirstPathComponent(String path) {
    int index = path.indexOf(File.separatorChar);
    return index == -1 ? path : path.substring(0, index);
  }

  private static boolean isExternalWorkspacePath(String firstPathComponent) {
    return firstPathComponent.equals("external");
  }
}
