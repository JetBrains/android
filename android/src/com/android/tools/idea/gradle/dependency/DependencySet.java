/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependency;

import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.dependency.Dependency.SUPPORTED_SCOPES;

/**
 * Collection of an IDEA module's dependencies.
 */
class DependencySet {
  private final Map<String, Dependency> myDependenciesByName = Maps.newHashMap();

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
  void add(@NotNull Dependency dependency) {
    Dependency storedDependency = myDependenciesByName.get(dependency.getName());
    if (storedDependency == null ||
        SUPPORTED_SCOPES.indexOf(dependency.getScope()) < SUPPORTED_SCOPES.indexOf(storedDependency.getScope())) {
      myDependenciesByName.put(dependency.getName(), dependency);
    }
  }

  Collection<Dependency> getValues() {
    return myDependenciesByName.values();
  }
}
