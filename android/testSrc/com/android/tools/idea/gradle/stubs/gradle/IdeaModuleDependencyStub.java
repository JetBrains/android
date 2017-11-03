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
package com.android.tools.idea.gradle.stubs.gradle;

import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.jetbrains.annotations.NotNull;

public class IdeaModuleDependencyStub implements IdeaModuleDependency {
  @NotNull private final IdeaModuleStub myModule;

  public IdeaModuleDependencyStub(@NotNull IdeaModuleStub module) {
    myModule = module;
  }

  @Override
  public String getTargetModuleName() {
    return myModule.getName();
  }

  @Override
  public IdeaModule getDependencyModule() {
    return myModule;
  }

  @Override
  public IdeaDependencyScope getScope() {
    return IdeaDependencyScopeStub.COMPILE;
  }

  @Override
  public boolean getExported() {
    return false;
  }
}
