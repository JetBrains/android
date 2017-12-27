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
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDependenciesSubject extends DependenciesSubject<ModuleOrderEntry> {
  @NotNull
  public static SubjectFactory<DependenciesSubject<ModuleOrderEntry>, Module> moduleDependencies() {
    return new SubjectFactory<DependenciesSubject<ModuleOrderEntry>, Module>() {
      @Override
      public ModuleDependenciesSubject getSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
        return new ModuleDependenciesSubject(failureStrategy, subject);
      }
    };
  }

  public ModuleDependenciesSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
    super(failureStrategy, subject);
  }

  @Override
  protected void collectDependencies(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    for (OrderEntry orderEntry : rootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        DependencyScope scope = moduleOrderEntry.getScope();
        getOrCreateMappingFor(scope).put(moduleOrderEntry.getModuleName(), moduleOrderEntry);
      }
    }
  }
}
