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
package com.android.tools.idea.testing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static junit.framework.Assert.assertNotNull;

public class Modules {
  @NotNull private final Project myProject;

  public Modules(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public Module getAppModule() {
    String name = "app";
    return getModule(name);
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    Module module = ModuleManager.getInstance(myProject).findModuleByName(name);
    assertNotNull("Unable to find module with name '" + name + "'", module);
    return module;
  }

  @Nullable
  public Module findModule(@NotNull String name) {
    return ModuleManager.getInstance(myProject).findModuleByName(name);
  }
}
