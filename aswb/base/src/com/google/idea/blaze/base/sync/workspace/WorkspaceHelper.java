/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** External-workspace-aware resolution of workspace paths. */
public class WorkspaceHelper {

  private static class Workspace {
    private final WorkspaceRoot root;
    @Nullable private final String externalWorkspaceName;

    private Workspace(WorkspaceRoot root, @Nullable String externalWorkspaceName) {
      this.root = root;
      this.externalWorkspaceName = externalWorkspaceName;
    }
  }

  @Nullable
  public static WorkspaceRoot resolveExternalWorkspace(Project project, String workspaceName) {
    Map<String, WorkspaceRoot> externalWorkspaces = getExternalWorkspaceRoots(project);
    return externalWorkspaces != null ? externalWorkspaces.get(workspaceName) : null;
  }

  /** Resolves the parent blaze package corresponding to this label. */
  @Nullable
  public static File resolveBlazePackage(Project project, Label label) {
    if (!label.isExternal()) {
      WorkspacePathResolver pathResolver =
          WorkspacePathResolverProvider.getInstance(project).getPathResolver();
      return pathResolver != null ? pathResolver.resolveToFile(label.blazePackage()) : null;
    }
    Map<String, WorkspaceRoot> externalWorkspaces = getExternalWorkspaceRoots(project);
    if (externalWorkspaces == null) {
      return null;
    }
    WorkspaceRoot root = externalWorkspaces.get(label.externalWorkspaceName());
    return root != null ? root.fileForPath(label.blazePackage()) : null;
  }

  @Nullable
  public static WorkspacePath resolveWorkspacePath(Project project, File absoluteFile) {
    Workspace workspace = resolveWorkspace(project, absoluteFile);
    return workspace != null ? workspace.root.workspacePathForSafe(absoluteFile) : null;
  }

  /** Converts a file to the corresponding BUILD label for this project, if valid. */
  @Nullable
  public static Label getBuildLabel(Project project, File absoluteFile) {
    Workspace workspace = resolveWorkspace(project, absoluteFile);
    if (workspace == null) {
      return null;
    }
    WorkspacePath workspacePath = workspace.root.workspacePathForSafe(absoluteFile);
    if (workspacePath == null) {
      return null;
    }
    return deriveLabel(project, workspace, workspacePath);
  }

  @Nullable
  private static Workspace resolveWorkspace(Project project, File absoluteFile) {
    WorkspacePathResolver pathResolver =
        WorkspacePathResolverProvider.getInstance(project).getPathResolver();
    if (pathResolver == null) {
      return null;
    }

    // try project workspace first
    WorkspaceRoot root = pathResolver.findWorkspaceRoot(absoluteFile);
    if (root != null) {
      return new Workspace(root, null);
    }

    Map<String, WorkspaceRoot> externalWorkspaces = getExternalWorkspaceRoots(project);
    if (externalWorkspaces == null) {
      return null;
    }
    for (Entry<String, WorkspaceRoot> entry : externalWorkspaces.entrySet()) {
      root = entry.getValue();
      WorkspacePath workspacePath = root.workspacePathForSafe(absoluteFile);
      if (workspacePath != null) {
        return new Workspace(root, entry.getKey());
      }
    }
    return null;
  }

  private static Label deriveLabel(
      Project project, Workspace workspace, WorkspacePath workspacePath) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    File file = workspace.root.fileForPath(workspacePath);
    if (provider.isBuildFile(file.getName())) {
      return Label.create(
          workspace.externalWorkspaceName,
          workspace.root.workspacePathFor(file.getParentFile()),
          TargetName.create("__pkg__"));
    }
    WorkspacePath packagePath = getPackagePath(provider, workspace.root, workspacePath);
    if (packagePath == null) {
      return null;
    }
    TargetName targetName =
        TargetName.createIfValid(
            FileUtil.getRelativePath(workspace.root.fileForPath(packagePath), file));
    return targetName != null
        ? Label.create(workspace.externalWorkspaceName, packagePath, targetName)
        : null;
  }

  private static WorkspacePath getPackagePath(
      BuildSystemProvider provider, WorkspaceRoot root, WorkspacePath workspacePath) {
    File file = root.fileForPath(workspacePath).getParentFile();
    while (file != null && FileUtil.isAncestor(root.directory(), file, false)) {
      ProgressManager.checkCanceled();
      if (provider.findBuildFileInDirectory(file) != null) {
        return root.workspacePathFor(file);
      }
      file = file.getParentFile();
    }
    return null;
  }

  @Nullable
  private static synchronized Map<String, WorkspaceRoot> getExternalWorkspaceRoots(
      Project project) {
    if (Blaze.getBuildSystemName(project) == BuildSystemName.Blaze) {
      return ImmutableMap.of();
    }
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      return ImmutableMap.of();
    }
    return SyncCache.getInstance(project)
        .get(WorkspaceHelper.class, WorkspaceHelper::enumerateExternalWorkspaces);
  }

  @SuppressWarnings("unused")
  private static Map<String, WorkspaceRoot> enumerateExternalWorkspaces(
      Project project, BlazeProjectData blazeProjectData) {
    FileOperationProvider provider = FileOperationProvider.getInstance();
    File[] children = provider.listFiles(getExternalSourceRoot(blazeProjectData));
    if (children == null) {
      return ImmutableMap.of();
    }
    return Arrays.stream(children)
        .filter(provider::isDirectory)
        .collect(Collectors.toMap(File::getName, WorkspaceRoot::new));
  }

  @VisibleForTesting
  public static File getExternalSourceRoot(BlazeProjectData projectData) {
    return new File(projectData.getBlazeInfo().getOutputBase(), "external");
  }
}
