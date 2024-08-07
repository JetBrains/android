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
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Converts workspace-relative paths to absolute files with a minimum of file system calls
 * (typically none).
 */
public interface WorkspacePathResolver extends ProtoWrapper<ProjectData.WorkspacePathResolver> {
  /** Resolves a workspace path to an absolute file. */
  default File resolveToFile(WorkspacePath workspacepath) {
    return resolveToFile(workspacepath.relativePath());
  }

  /** Resolves a workspace relative path to an absolute file. */
  default File resolveToFile(String workspaceRelativePath) {
    File packageRoot = findPackageRoot(workspaceRelativePath);
    return new File(packageRoot, workspaceRelativePath);
  }

  /**
   * This method should be used for directories. Returns all workspace files corresponding to the
   * given workspace path.
   */
  ImmutableList<File> resolveToIncludeDirectories(WorkspacePath relativePath);

  /** Finds the package root directory that a workspace relative path is in. */
  File findPackageRoot(String relativePath);

  /**
   * Finds the workspace root directory that an absolute file lies under. Returns null if the file
   * is not in a known workspace.
   */
  @Nullable
  WorkspaceRoot findWorkspaceRoot(File absoluteFile);

  /**
   * Given a resolved, absolute file, returns the corresponding {@link WorkspacePath}. Returns null
   * if the file is not in the workspace.
   */
  @Nullable
  WorkspacePath getWorkspacePath(File absoluteFile);

  static WorkspacePathResolver fromProto(ProjectData.WorkspacePathResolver proto) {
    return Arrays.stream(Extractor.EP_NAME.getExtensions())
        .map(extractor -> extractor.extract(proto))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Deserialize a {@link WorkspacePathResolver} from a proto message.
   *
   * <p>{@link WorkspacePathResolver#fromProto} will use the first non-null result from {@link
   * Extractor#extract}. The EP ordering dictates which is chosen.
   */
  interface Extractor {
    ExtensionPointName<Extractor> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.WorkspacePathResolverExtractor");

    @Nullable
    WorkspacePathResolver extract(ProjectData.WorkspacePathResolver proto);
  }
}
