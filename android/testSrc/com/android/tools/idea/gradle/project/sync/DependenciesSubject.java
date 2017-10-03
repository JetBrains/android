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
package com.android.tools.idea.gradle.project.sync;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExportableOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;

public abstract class DependenciesSubject<T  extends ExportableOrderEntry> extends Subject<DependenciesSubject<T>, Module> {
  @NotNull protected final Map<DependencyScope, Map<String, T>> myDependenciesByNameAndScope = new HashMap<>();

  protected DependenciesSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
    super(failureStrategy, subject);
    if (subject != null) {
      //noinspection AbstractMethodCallInConstructor
      collectDependencies(subject);
    }
  }

  protected abstract void collectDependencies(@NotNull Module module);

  @NotNull
  protected final Map<String, T> getOrCreateMappingFor(@NotNull DependencyScope scope) {
    Map<String, T> dependenciesByName = myDependenciesByNameAndScope.get(scope);
    if (dependenciesByName == null) {
      dependenciesByName = new HashMap<>();
      myDependenciesByNameAndScope.put(scope, dependenciesByName);
    }
    return dependenciesByName;
  }

  public void contains(@NotNull String dependencyName, @NotNull DependencyScope...scopes) {
    if (!getDependencyNames(scopes).contains(dependencyName)) {
      fail("has a dependency with name '" + dependencyName + "' in scope(s): " + Arrays.toString(scopes));
    }
  }

  public final void containsMatching(boolean isExported, @NotNull String dependencyNameRegex, @NotNull DependencyScope... scopes) {
    T matchingDependency = null;
    for (T dependency : getDependencies(scopes)) {
      if (dependency.getPresentableName().matches(dependencyNameRegex)) {
        matchingDependency = dependency;
        break;
      }
    }
    if (matchingDependency == null) {
      fail("has a dependency with name matching '" + dependencyNameRegex + "' in scope(s): " + Arrays.toString(scopes));
    }
    if (matchingDependency.isExported() != isExported) {
      failureStrategy.fail("Not true that " + matchingDependency.getPresentableName() + " has exported set to " + isExported);
    }
  }

  @NotNull
  private Set<T> getDependencies(@NotNull DependencyScope... scopes) {
    Set<T> dependencies = new HashSet<>();
    for (DependencyScope scope : scopes) {
      Map<String, T> dependenciesByName = myDependenciesByNameAndScope.get(scope);
      if (dependenciesByName != null) {
        dependencies.addAll(dependenciesByName.values());
      }
    }
    return dependencies;
  }

  public final void containsMatching(@NotNull String dependencyNameRegex, @NotNull DependencyScope...scopes) {
    boolean found = false;
    for (String name : getDependencyNames(scopes)) {
      if (name.matches(dependencyNameRegex)) {
        found = true;
        break;
      }
    }
    if (!found) {
      fail("has a dependency with name matching '" + dependencyNameRegex + "' in scope(s): " + Arrays.toString(scopes));
    }
  }

  public final void doesNotContain(@NotNull String dependencyName, @NotNull DependencyScope...scopes) {
    assertThat(getDependencyNames(scopes)).doesNotContain(dependencyName);
  }

  public final void doesNotHaveDependencies() {
    assertThat(getDependencyNames(DependencyScope.values())).isEmpty();
  }

  @NotNull
  private Set<String> getDependencyNames(@NotNull DependencyScope...scopes) {
    Set<String> names = new HashSet<>();
    for (DependencyScope scope : scopes) {
      Map<String, T> dependenciesByName = myDependenciesByNameAndScope.get(scope);
      if (dependenciesByName != null) {
        names.addAll(dependenciesByName.keySet());
      }
    }
    return names;
  }

  public final void hasDependency(@NotNull String dependencyName, @NotNull DependencyScope scope, boolean isExported) {
    Map<String, T> dependenciesByName = myDependenciesByNameAndScope.get(scope);
    T dependency = dependenciesByName.get(dependencyName);
    if (dependency == null) {
      fail("has a dependency with name '" + dependencyName + "' in scope(s): " + scope.toString());
    }
    else {
      if (dependency.isExported() != isExported) {
        failureStrategy.fail("Not true that " + dependencyName + " has exported set to " + isExported);
      }
    }
  }
}
