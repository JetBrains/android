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
package com.google.idea.blaze.base.bazel;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import java.io.File;
import javax.annotation.Nullable;

/** Utility methods for working with workspace paths. */
public interface WorkspaceRootProvider {

  /** Checks whether the given directory is a valid workspace root. */
  boolean isWorkspaceRoot(File file);

  /**
   * Checks whether the file or one of its ancestors is a valid workspace root.<br>
   * This should only be called when no Project is available. Otherwise, use
   * WorkspaceRoot.isInWorkspace.
   */
  default boolean isInWorkspace(File file) {
    return findWorkspaceRoot(file) != null;
  }

  /**
   * If the given file is inside a workspace, returns the workspace root. Otherwise returns null.
   */
  @Nullable
  WorkspaceRoot findWorkspaceRoot(File file);
}
