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
package com.android.tools.idea.gradle.structure.model.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsJavaDependency extends PsDependency {
  protected PsJavaDependency(@NotNull PsJavaModule parent,
                             @Nullable DependencyModel parsedModel) {
    super(parent, parsedModel);
  }

  @NotNull
  public JavaProject getGradleModel() {
    return getParent().getGradleModel();
  }

  @Override
  @NotNull
  public PsJavaModule getParent() {
    return (PsJavaModule)super.getParent();
  }
}
