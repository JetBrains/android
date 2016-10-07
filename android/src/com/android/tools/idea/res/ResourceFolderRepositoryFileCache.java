/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res;

import com.android.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface ResourceFolderRepositoryFileCache {

  /**
   * Returns the blob directory that should be used to read/write the file cache for the given resourceDir.
   * If cache is invalidated returns null. The caller should avoid reading/writing to the cache in that case.
   *
   * @param project the project containing the resource directory
   * @param resourceDir the resource directory which is the source of truth
   * @return the cache directory, or null if all caches are invalidated
   */
  @Nullable File getResourceDir(@NotNull Project project, @NotNull VirtualFile resourceDir);

  /**
   * Returns the root directory where caches for all projects are stored.
   * Doesn't matter if the cache is invalidated.
   *
   * @return the root dir, or null on IO exceptions
   */
  @Nullable File getRootDir();

  /**
   * Returns the parent directory where caches for a given project is stored.
   * Doesn't matter if the cache is invalidated.
   *
   * @return the project cache dir, or null on IO exceptions
   */
  @Nullable File getProjectDir(@NotNull Project currentProject);

  /**
   * Mark the cache invalid.
   */
  void invalidate();

  /**
   * @return true if the cache is valid (not invalidated).
   */
  boolean isValid();

  /**
   * Delete the cache from disk, clearing the invalidation stamp.
   */
  void delete();

  /**
   * Stamp the cache with the given version.
   */
  @VisibleForTesting
  void stampVersion(@NotNull File rootDir, int version);
}
