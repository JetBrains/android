/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

abstract class ArtifactRepositorySearch {
  @NotNull
  abstract String getName();

  /**
   * Indicates whether search results are paginated (if supported by the repository.)
   * @return {@code true} if the search results are paginated; {@code false} otherwise.
   */
  abstract boolean supportsPagination();

  @NotNull
  abstract SearchResult start(@NotNull Request request) throws IOException;

  static class Request {
    @NotNull final String artifactName;
    @Nullable final String groupId;
    final int rows;
    final int start;

    /**
     * Creates a new {@link Request}.
     * @param artifactName the name of the artifact to look for.
     * @param groupId the group ID of the artifact (optional.)
     * @param rows number of rows to retrieve.
     * @param start start zero-based starting position of the search (useful when paginating results.)
     */
    Request(@NotNull String artifactName, @Nullable String groupId, int rows, int start) {
      this.artifactName = artifactName;
      this.groupId = groupId;
      this.rows = rows;
      this.start = start;
    }
  }
}
