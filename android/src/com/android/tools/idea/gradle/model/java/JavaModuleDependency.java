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
package com.android.tools.idea.gradle.model.java;

import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Dependency to a Java module.
 */
public class JavaModuleDependency implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myModuleName;
  @Nullable private final String myScope;
  private final boolean myExported;

  @Nullable
  public static JavaModuleDependency copy(IdeaModuleDependency original) {
    IdeaModule module = original.getDependencyModule();
    if (module != null && isNotEmpty(module.getName())) {
      String scope = null;
      IdeaDependencyScope originalScope = original.getScope();
      if (originalScope != null) {
        scope = originalScope.getScope();
      }
      return new JavaModuleDependency(module.getName(), scope, original.getExported());
    }
    return null;
  }

  public JavaModuleDependency(@NotNull String moduleName, @Nullable String scope, boolean exported) {
    myModuleName = moduleName;
    myScope = scope;
    myExported = exported;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Nullable
  public String getScope() {
    return myScope;
  }

  public boolean isExported() {
    return myExported;
  }
}
