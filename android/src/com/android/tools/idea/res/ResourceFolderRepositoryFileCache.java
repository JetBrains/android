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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ResourceFolderRepositoryFileCache {
  /**
   * Creates a {@link ResourceFolderRepositoryCachingData} object for the given project and resource directory.
   *
   * @param project the project
   * @param resourceDir the resource directory
   * @param cacheCreationExecutor the executor used for creating a cache file, or null if the cache file
   *     should not be created if it doesn't exist or is out of date.
   * @return the created {@link ResourceFolderRepositoryCachingData} object, or null if {@code resourceDir}
   *     is invalid, or the version of the Android plugin cannot be determined, or if the parent directory of
   *     the cache file cannot be determined or is inaccessible.
   */
  @Nullable
  ResourceFolderRepositoryCachingData getCachingData(@NotNull Project project,
                                                     @NotNull VirtualFile resourceDir,
                                                     @Nullable Executor cacheCreationExecutor);


  /**
   * Creates an empty cache directory for the given project.
   */
  void createDirForProject(@NotNull Project project) throws IOException;

  /**
   * Marks the cache invalid.
   */
  void invalidate();
}
