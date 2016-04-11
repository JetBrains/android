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

import java.util.Collections;
import java.util.List;

public class SearchResult {
  @NotNull private final String myRepositoryName;
  @NotNull private final List<String> myData;
  @Nullable private final Exception myError;
  private final int myTotalFound;

  SearchResult(@NotNull String repositoryName, @NotNull Exception error) {
    this(repositoryName, Collections.emptyList(), error, 0);
  }

  SearchResult(@NotNull String repositoryName, @NotNull List<String> data, int totalFound) {
    this(repositoryName, data, null, totalFound);
  }

  private SearchResult(@NotNull String repositoryName, @NotNull List<String> data, @Nullable Exception error, int totalFound) {
    myRepositoryName = repositoryName;
    myData = data;
    myError = error;
    myTotalFound = totalFound;
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryName;
  }

  @NotNull
  public List<String> getData() {
    return myData;
  }

  @Nullable
  public Exception getError() {
    return myError;
  }

  public int getTotalFound() {
    return myTotalFound;
  }
}
