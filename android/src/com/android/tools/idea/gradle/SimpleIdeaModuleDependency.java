/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class SimpleIdeaModuleDependency implements IdeaDependency, Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final IdeaDependencyScope myScope;
  private final boolean myExported;

  @Nullable
  public static SimpleIdeaModuleDependency copy(IdeaModuleDependency original) {
    IdeaModule module = original.getDependencyModule();
    if (module != null && isNotEmpty(module.getName())) {
      return new SimpleIdeaModuleDependency(module.getName(), original.getScope(), original.getExported());
    }
    return null;
  }

  public SimpleIdeaModuleDependency(@NotNull String moduleName, @NotNull IdeaDependencyScope scope, boolean exported) {
    myModuleName = moduleName;
    myScope = scope;
    myExported = exported;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  @NotNull
  public IdeaDependencyScope getScope() {
    return myScope;
  }

  @Override
  public boolean getExported() {
    return myExported;
  }
}
