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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Collection of an IDEA module's dependencies.
 */
public class DependencySet {
  @NotNull public static final DependencySet EMPTY = new DependencySet() {
    @Override
    void add(@NotNull LibraryDependency dependency) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addAll(DependencySet other) {
      throw new UnsupportedOperationException();
    }

    @Override
    void add(@NotNull ModuleDependency dependency) {
      throw new UnsupportedOperationException();
    }
  };

  @NotNull public static final DependencySet THROWING = new DependencySet() {
    @Override
    void add(@NotNull LibraryDependency dependency) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addAll(DependencySet other) {
      throw new UnsupportedOperationException();
    }

    @Override
    void add(@NotNull ModuleDependency dependency) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull ImmutableCollection<LibraryDependency> onLibraries() {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull ImmutableCollection<ModuleDependency> onModules() {
      throw new UnsupportedOperationException();
    }
  };

  // Use linked list to maintain insertion order.
  private final Set<LibraryDependency> myDependencies = new LinkedHashSet<>();
  private final Map<Module, ModuleDependency> myModuleDependenciesByModule = Maps.newLinkedHashMap();

  DependencySet() {
  }

  /**
   * Adds the given dependency to this collection. If this collection already has a dependency under the same name and artifacts, the
   * dependency with the wider scope is stored: {@link com.intellij.openapi.roots.DependencyScope#COMPILE} has wider scope than
   * {@link com.intellij.openapi.roots.DependencyScope#TEST}.
   * <p/>
   * It is not uncommon that the Android Gradle plug-in lists the same dependency as explicitly having both "compile" and "test" scopes. In
   * IDEA there is no such distinction, a dependency with "compile" scope is also available to test code.
   *
   * @param dependency the dependency to add.
   */
  void add(@NotNull LibraryDependency dependency) {
    myDependencies.add(dependency);
  }

  /**
   * Adds all the dependencies in other DependencySet to this
   *
   * @param other DependencySet to be added to this
   */
  public void addAll(DependencySet other) {
    for (LibraryDependency libraryDependency : other.onLibraries()) {
      add(libraryDependency);
    }
    for (ModuleDependency moduleDependency : other.onModules()) {
      add(moduleDependency);
    }
  }

  private static boolean areSameArtifact(@NotNull LibraryDependency d1, @NotNull LibraryDependency d2) {
    return Arrays.equals(d1.getBinaryPaths(), d2.getBinaryPaths());
  }

  /**
   * Adds the given dependency to this collection. If this collection already has a dependency under the same name, the dependency with the
   * wider scope is stored: {@link com.intellij.openapi.roots.DependencyScope#COMPILE} has wider scope than
   * {@link com.intellij.openapi.roots.DependencyScope#TEST}.
   * <p>
   * It is not uncommon that the Android Gradle plug-in lists the same dependency as explicitly having both "compile" and "test" scopes. In
   * IDEA there is no such distinction, a dependency with "compile" scope is also available to test code.
   *
   * @param dependency the dependency to add.
   */
  void add(@NotNull ModuleDependency dependency) {
    Module module = dependency.getModule();
    Dependency storedDependency = myModuleDependenciesByModule.get(module);
    if (storedDependency == null) {
      myModuleDependenciesByModule.put(module, dependency);
    }
  }

  @NotNull
  public ImmutableCollection<LibraryDependency> onLibraries() {
    return ImmutableList.copyOf(myDependencies);
  }

  @NotNull
  public ImmutableCollection<ModuleDependency> onModules() {
    return ImmutableList.copyOf(myModuleDependenciesByModule.values());
  }
}
