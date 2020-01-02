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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An IDEA module's dependency on another IDEA module.
 */
public class ModuleDependency extends Dependency {
  @NotNull private final String myGradlePath;
  @Nullable private final Module myModule;

  /**
   * Creates a new {@link ModuleDependency}.
   *
   * @param gradlePath the Gradle path of the project that maps to the IDEA module to depend on.
   * @param scope      the scope of the dependency. Supported values are {@link DependencyScope#COMPILE} and {@link DependencyScope#TEST}.
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  @VisibleForTesting
  public ModuleDependency(@NotNull String gradlePath, @NotNull DependencyScope scope, @Nullable Module module) {
    super(scope);
    myGradlePath = gradlePath;
    myModule = module;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ModuleDependency)) {
      return false;
    }
    ModuleDependency that = (ModuleDependency)o;
    return Objects.equals(myGradlePath, that.myGradlePath);
  }

  @Override
  public int hashCode() {
    return myGradlePath.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "gradlePath=" + myGradlePath +
           ", scope=" + getScope() +
           "]";
  }
}
