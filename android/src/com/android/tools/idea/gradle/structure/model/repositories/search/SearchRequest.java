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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class SearchRequest {
  @NotNull private final String myArtifactName;

  @Nullable private final String myGroupId;

  private final int myRowCount;
  private final int myStart;

  /**
   * Creates a new {@link SearchRequest}.
   *
   * @param artifactName the name of the artifact to look for.
   * @param groupId      the group ID of the artifact (optional.)
   * @param rowCount     number of rows to retrieve.
   * @param start        start zero-based starting position of the search (useful when paginating search results.)
   */
  public SearchRequest(@NotNull String artifactName, @Nullable String groupId, int rowCount, int start) {
    myArtifactName = artifactName;
    myGroupId = groupId;
    myRowCount = rowCount;
    myStart = start;
  }

  @NotNull
  String getArtifactName() {
    return myArtifactName;
  }

  @Nullable
  String getGroupId() {
    return myGroupId;
  }

  int getRowCount() {
    return myRowCount;
  }

  int getStart() {
    return myStart;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SearchRequest that = (SearchRequest)o;
    return myRowCount == that.myRowCount &&
           myStart == that.myStart &&
           Objects.equals(myArtifactName, that.myArtifactName) &&
           Objects.equals(myGroupId, that.myGroupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myArtifactName, myGroupId, myRowCount, myStart);
  }

  @Override
  public String toString() {
    return "{artifact='" + myArtifactName + '\'' +
           ", group='" + myGroupId + '\'' +
           ", rowCount=" + myRowCount +
           ", start=" + myStart +
           '}';
  }
}
