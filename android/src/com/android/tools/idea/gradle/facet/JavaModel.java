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
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaModel {
  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";
  @NotNull private final List<IdeaContentRoot> myContentRoots;
  @NotNull private final List<IdeaModuleDependency> myModuleDependencies;
  @NotNull private final List<IdeaSingleEntryLibraryDependency> myLibraryDependencies;
  @NotNull private final List<String> myUnresolvedDependencyNames;
  @NotNull private final ExtIdeaCompilerOutput myCompilerOutput;

  @NotNull
  public static JavaModel newJavaModel(@NotNull IdeaModule module, @NotNull ModuleExtendedModel model) {
    List<IdeaContentRoot> contentRoots = Lists.newArrayList();
    for (IdeaContentRoot root : getContentRoots(module, model)) {
      contentRoots.add(root);
    }

    List<IdeaModuleDependency> moduleDependencies = Lists.newArrayList();
    List<IdeaSingleEntryLibraryDependency> libraryDependencies = Lists.newArrayList();
    List<String> unresolvedDependencyNames = Lists.newArrayList();

    for (IdeaDependency dependency : getDependencies(module)) {
      if (dependency instanceof IdeaModuleDependency) {
        moduleDependencies.add((IdeaModuleDependency)dependency);
        continue;
      }
      if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency libDependency = (IdeaSingleEntryLibraryDependency)dependency;
        if (isResolved(libDependency)) {
          libraryDependencies.add(libDependency);
          continue;
        }
        String name = getUnresolvedDependencyName(libDependency);
        if (name != null) {
          unresolvedDependencyNames.add(name);
        }
      }
    }
    return new JavaModel(contentRoots, moduleDependencies, libraryDependencies, unresolvedDependencyNames, model.getCompilerOutput());
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule module, @NotNull ModuleExtendedModel model) {
    Collection<? extends IdeaContentRoot> contentRoots = model.getContentRoots();
    if (contentRoots == null) {
      contentRoots = module.getContentRoots();
    }
    return contentRoots != null ? contentRoots : Collections.<IdeaContentRoot>emptyList();
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(IdeaModule module) {
    List<? extends IdeaDependency> dependencies = module.getDependencies().getAll();
    return dependencies != null ? dependencies : Collections.<IdeaDependency>emptyList();
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

  public JavaModel(@NotNull List<IdeaContentRoot> contentRoots,
                   @NotNull List<IdeaModuleDependency> moduleDependencies,
                   @NotNull List<IdeaSingleEntryLibraryDependency> libraryDependencies,
                   @NotNull List<String> unresolvedDependencyNames,
                   @NotNull ExtIdeaCompilerOutput compilerOutput) {
    myContentRoots = contentRoots;
    myModuleDependencies = moduleDependencies;
    myLibraryDependencies = libraryDependencies;
    myUnresolvedDependencyNames = unresolvedDependencyNames;
    myCompilerOutput = compilerOutput;
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

  @NotNull
  public ExtIdeaCompilerOutput getCompilerOutput() {
    return myCompilerOutput;
  }
}
