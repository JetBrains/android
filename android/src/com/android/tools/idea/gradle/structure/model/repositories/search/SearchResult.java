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

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;

public class SearchResult {
  @NotNull private final String myRepositoryName;
  @NotNull private final List<FoundArtifact> myArtifacts;
  @Nullable private final Exception myError;
  private final int myTotalFound;

  SearchResult(@NotNull String repositoryName, @NotNull Exception error) {
    this(repositoryName, Collections.emptyList(), error, 0);
  }

  SearchResult(@NotNull String repositoryName, @NotNull List<FoundArtifact> artifacts, int totalFound) {
    this(repositoryName, artifacts, null, totalFound);
  }

  private SearchResult(@NotNull String repositoryName, @NotNull List<FoundArtifact> artifacts, @Nullable Exception error, int totalFound) {
    myRepositoryName = repositoryName;
    myArtifacts = artifacts;
    myError = error;
    myTotalFound = totalFound;
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryName;
  }

  @NotNull
  public List<FoundArtifact> getArtifacts() {
    return myArtifacts;
  }

  @Nullable
  public Exception getError() {
    return myError;
  }

  public int getTotalFound() {
    return myTotalFound;
  }

  @TestOnly
  @NotNull
  public List<String> getArtifactCoordinates() {
    List<String> coordinates = Lists.newArrayList();
    myArtifacts.forEach(artifact -> coordinates.addAll(artifact.getCoordinates()));
    return coordinates;
  }

  @Override
  public String toString() {
    return "{repository='" + myRepositoryName + '\'' +
           ", artifacts=" + myArtifacts +
           ", error=" + myError +
           ", totalFound=" + myTotalFound +
           '}';
  }
}