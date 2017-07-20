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
import com.google.common.truth.SubjectFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LibraryDependenciesSubject extends DependenciesSubject<LibraryOrderEntry> {
  @NotNull
  public static SubjectFactory<DependenciesSubject<LibraryOrderEntry>, Module> libraryDependencies() {
    return new SubjectFactory<DependenciesSubject<LibraryOrderEntry>, Module>() {
      @Override
      public LibraryDependenciesSubject getSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
        return new LibraryDependenciesSubject(failureStrategy, subject);
      }
    };
  }

  public LibraryDependenciesSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
    super(failureStrategy, subject);
  }

  @Override
  protected void collectDependencies(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    for (OrderEntry orderEntry : rootManager.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        DependencyScope scope = libraryOrderEntry.getScope();
        getOrCreateMappingFor(scope).put(libraryOrderEntry.getLibraryName(), libraryOrderEntry);
      }
    }
  }
}
