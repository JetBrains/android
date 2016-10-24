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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

public class ExpectedModuleDependency {
  @NotNull private final Module myModule;
  @NotNull private final DependencyScope myScope;
  private final boolean myExported;

  public ExpectedModuleDependency(@NotNull Module module, @NotNull DependencyScope scope, boolean exported) {
    this.myModule = module;
    this.myScope = scope;
    this.myExported = exported;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public boolean isExported() {
    return myExported;
  }

  @Override
  public String toString() {
    return "ModuleDependency{" +
           "module=" + myModule +
           ", scope=" + myScope +
           ", exported=" + myExported +
           '}';
  }
}
