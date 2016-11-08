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
package com.android.tools.idea.gradle.project.sync;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

public class LibraryDependenciesSubject extends Subject<LibraryDependenciesSubject, Module> {
  @NotNull
  public static SubjectFactory<LibraryDependenciesSubject, Module> libraryDependencies() {
    return new SubjectFactory<LibraryDependenciesSubject, Module>() {
      @Override
      public LibraryDependenciesSubject getSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
        return new LibraryDependenciesSubject(failureStrategy, subject);
      }
    };
  }

  private final Map<String, LibraryOrderEntry> myLibraryDependenciesByName = new HashMap<>();

  public LibraryDependenciesSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
    super(failureStrategy, subject);
    if (subject != null) {
      collectLibraryDependencies(subject);
    }
  }

  private void collectLibraryDependencies(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    // Collect direct dependencies first.
    for (OrderEntry orderEntry : rootManager.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        if (libraryOrderEntry.isExported()) {
          myLibraryDependenciesByName.put(libraryOrderEntry.getLibraryName(), libraryOrderEntry);
        }
      }
    }

    // Collect transitive library dependencies.
    for (Module dependency : rootManager.getModuleDependencies()) {
      collectLibraryDependencies(dependency);
    }
  }

  @NotNull
  public LibraryDependenciesSubject contains(@NotNull String libraryName) {
    assertThat(getLibraryNames()).contains(libraryName);
    return this;
  }

  @NotNull
  public LibraryDependenciesSubject doesNotContain(@NotNull String libraryName) {
    assertThat(getLibraryNames()).doesNotContain(libraryName);
    return this;
  }

  @NotNull
  public LibraryDependenciesSubject isEmpty() {
    assertThat(getLibraryNames()).isEmpty();
    return this;
  }

  @NotNull
  private Set<String> getLibraryNames() {
    return myLibraryDependenciesByName.keySet();
  }
}
