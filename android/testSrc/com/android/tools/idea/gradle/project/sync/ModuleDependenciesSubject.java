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
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ModuleDependenciesSubject extends Subject<ModuleDependenciesSubject, Module> {
  @NotNull
  public static SubjectFactory<ModuleDependenciesSubject, Module> moduleDependencies() {
    return new SubjectFactory<ModuleDependenciesSubject, Module>() {
      @Override
      public ModuleDependenciesSubject getSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
        return new ModuleDependenciesSubject(failureStrategy, subject);
      }
    };
  }

  private final Map<String, ModuleOrderEntry> myModuleDependenciesByName = new HashMap<>();

  public ModuleDependenciesSubject(FailureStrategy failureStrategy, @Nullable Module subject) {
    super(failureStrategy, subject);
    if (subject != null) {
      collectModuleDependencies(subject);
    }
  }

  private void collectModuleDependencies(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

    // Collect direct dependencies first.
    for (OrderEntry orderEntry : rootManager.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        if (moduleOrderEntry.isExported()) {
          myModuleDependenciesByName.put(moduleOrderEntry.getModuleName(), moduleOrderEntry);
        }
      }
    }

    // Collect transitive library dependencies.
    for (Module dependency : rootManager.getModuleDependencies()) {
      collectModuleDependencies(dependency);
    }
  }

  @NotNull
  public ModuleDependenciesSubject contains(@NotNull String moduleName) {
    assertThat(getModuleNames()).contains(moduleName);
    return this;
  }

  @NotNull
  public ModuleDependenciesSubject contains(@NotNull ExpectedModuleDependency dependency) {
    String moduleName = dependency.getModule().getName();
    ModuleOrderEntry orderEntry = myModuleDependenciesByName.get(moduleName);
    assertNotNull("dependency on module '" + moduleName + "'", orderEntry);
    assertEquals("scope", orderEntry.getScope(), dependency.getScope());
    assertEquals("exported", orderEntry.isExported(), dependency.isExported());
    return this;
  }

  @NotNull
  public ModuleDependenciesSubject doesNotContain(@NotNull String moduleName) {
    assertThat(getModuleNames()).doesNotContain(moduleName);
    return this;
  }

  @NotNull
  public ModuleDependenciesSubject isEmpty() {
    assertThat(getModuleNames()).isEmpty();
    return this;
  }

  @NotNull
  private Set<String> getModuleNames() {
    return myModuleDependenciesByName.keySet();
  }
}
