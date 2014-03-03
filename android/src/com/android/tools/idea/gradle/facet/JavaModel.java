/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.facet;

import com.google.common.collect.Lists;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class JavaModel {
  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  @NotNull private final List<IdeaContentRoot> myContentRoots = Lists.newArrayList();
  @NotNull private final List<IdeaModuleDependency> myModuleDependencies = Lists.newArrayList();
  @NotNull private final List<IdeaSingleEntryLibraryDependency> myLibraryDependencies = Lists.newArrayList();
  @NotNull private final List<String> myUnresolvedDependencyNames = Lists.newArrayList();

  public JavaModel(@NotNull Collection<? extends IdeaContentRoot> contentRoots, @NotNull List<? extends IdeaDependency> dependencies) {
    for (IdeaContentRoot contentRoot : contentRoots) {
      if (contentRoot != null) {
        myContentRoots.add(contentRoot);
      }
    }
    for (IdeaDependency dependency : dependencies) {
      if (dependency instanceof IdeaModuleDependency) {
        myModuleDependencies.add((IdeaModuleDependency)dependency);
      }
      else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency libDependency = (IdeaSingleEntryLibraryDependency)dependency;
        if (isResolved(libDependency)) {
          myLibraryDependencies.add(libDependency);
        }
        else {
          String name = getUnresolvedDependencyName(libDependency);
          if (name != null) {
            myUnresolvedDependencyNames.add(name);
          }
        }
      }
    }
  }

  private static boolean isResolved(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    return libraryName != null && !libraryName.startsWith(UNRESOLVED_DEPENDENCY_PREFIX);
  }

  @Nullable
  private static String getUnresolvedDependencyName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    if (libraryName == null) {
      return null;
    }
    // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
    // We report the unresolved dependency as 'commons-collections:commons-collections:3.2'
    return libraryName.substring(UNRESOLVED_DEPENDENCY_PREFIX.length()).replace(' ', ':');
  }

  @Nullable
  private static String getFileName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    File binaryPath = dependency.getFile();
    return binaryPath != null ? binaryPath.getName() : null;
  }

  @NotNull
  public List<IdeaContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public List<IdeaModuleDependency> getModuleDependencies() {
    return myModuleDependencies;
  }

  @NotNull
  public List<IdeaSingleEntryLibraryDependency> getLibraryDependencies() {
    return myLibraryDependencies;
  }

  @NotNull
  public List<String> getUnresolvedDependencyNames() {
    return myUnresolvedDependencyNames;
  }
}
