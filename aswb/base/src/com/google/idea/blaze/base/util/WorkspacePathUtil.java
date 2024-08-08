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
package com.google.idea.blaze.base.util;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.util.io.FileUtil;
import java.util.Collection;

/** Removes any duplicates or overlapping directories */
public class WorkspacePathUtil {

  /** Returns whether the given workspace path is a child of any workspace path. */
  public static boolean isUnderAnyWorkspacePath(
      Collection<WorkspacePath> ancestors, WorkspacePath child) {
    return ancestors
        .stream()
        .anyMatch(
            importRoot ->
                FileUtil.isAncestor(importRoot.relativePath(), child.relativePath(), false));
  }

  /** Removes any duplicates or overlapping directories */
  public static ImmutableSet<WorkspacePath> calculateMinimalWorkspacePaths(
      Collection<WorkspacePath> workspacePaths) {
    return calculateMinimalWorkspacePaths(workspacePaths, ImmutableList.of());
  }

  /** Removes any duplicates or overlapping or excluded directories. */
  public static ImmutableSet<WorkspacePath> calculateMinimalWorkspacePaths(
      Collection<WorkspacePath> workspacePaths, Collection<WorkspacePath> excludedPaths) {
    return workspacePaths
        .stream()
        .filter(path -> includePath(workspacePaths, excludedPaths, path))
        .collect(toImmutableSet());
  }

  private static boolean includePath(
      Collection<WorkspacePath> workspacePaths,
      Collection<WorkspacePath> excludedPaths,
      WorkspacePath path) {
    for (WorkspacePath excluded : excludedPaths) {
      if (FileUtil.isAncestor(excluded.relativePath(), path.relativePath(), false)) {
        return false;
      }
    }
    for (WorkspacePath otherDirectory : workspacePaths) {
      if (path.equals(otherDirectory)) {
        continue;
      }
      if (FileUtil.isAncestor(otherDirectory.relativePath(), path.relativePath(), true)) {
        return false;
      }
    }
    return true;
  }
}
