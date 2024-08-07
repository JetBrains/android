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

import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Provides information about whether files are inside the project, as of the most recent Blaze
 * sync. Unlike ProjectFileIndex, requires no file system operations.
 */
public interface WorkspaceFileFinder {

  /**
   * Returns true if this file was covered by the .blazeproject 'directories' section in the most
   * recent sync.
   */
  boolean isInProject(File file);

  /** Provides a {@link WorkspaceFileFinder}. */
  interface Provider {

    static Provider getInstance(Project project) {
      return project.getService(Provider.class);
    }

    /**
     * Returns a {@link WorkspaceFileFinder} for this project, or null if it's not a blaze/bazel
     * project.
     */
    @Nullable
    WorkspaceFileFinder getWorkspaceFileFinder();
  }
}
