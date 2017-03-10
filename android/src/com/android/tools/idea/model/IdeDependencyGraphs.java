/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GraphItem;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deep copy of {@link DependencyGraphs}.
 *
 * @see IdeAndroidProject
 */
final public class IdeDependencyGraphs implements DependencyGraphs, Serializable {
  @NotNull private final List<GraphItem> myCompileDependencies;
  @NotNull private final List<GraphItem> myPackageDependencies;
  @NotNull private final List<String> myProvidedLibraries;
  @NotNull private final List<String> mySkippedLibraries;

  public IdeDependencyGraphs(){
    myCompileDependencies = Collections.emptyList();
    myPackageDependencies = Collections.emptyList();
    myProvidedLibraries = Collections.emptyList();
    mySkippedLibraries = Collections.emptyList();
  }

  public IdeDependencyGraphs(@NotNull DependencyGraphs graphs) {
    myCompileDependencies = new ArrayList<>();
    for (GraphItem dependency : graphs.getCompileDependencies()) {
      myCompileDependencies.add(new IdeGraphItem(dependency));
    }

    myPackageDependencies = new ArrayList<>();
    for (GraphItem dependency : graphs.getPackageDependencies()) {
      myPackageDependencies.add(new IdeGraphItem(dependency));
    }

    myProvidedLibraries = new ArrayList<>(graphs.getProvidedLibraries());
    mySkippedLibraries = new ArrayList<>(graphs.getSkippedLibraries());
  }

  @Override
  @NotNull
  public List<GraphItem> getCompileDependencies() {
    return myCompileDependencies;
  }

  @Override
  @NotNull
  public List<GraphItem> getPackageDependencies() {
    return myPackageDependencies;
  }

  @Override
  @NotNull
  public List<String> getProvidedLibraries() {
    return myProvidedLibraries;
  }

  @Override
  @NotNull
  public List<String> getSkippedLibraries() {
    return mySkippedLibraries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DependencyGraphs)) return false;
    DependencyGraphs graphs = (DependencyGraphs)o;
    return Objects.equals(getCompileDependencies(), graphs.getCompileDependencies()) &&
           Objects.equals(getPackageDependencies(), graphs.getPackageDependencies()) &&
           Objects.equals(getProvidedLibraries(), graphs.getProvidedLibraries()) &&
           Objects.equals(getSkippedLibraries(), graphs.getSkippedLibraries());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getCompileDependencies(), getPackageDependencies(), getProvidedLibraries(), getSkippedLibraries());
  }
}
