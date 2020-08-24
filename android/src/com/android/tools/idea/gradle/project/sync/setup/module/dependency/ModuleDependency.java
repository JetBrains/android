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

import com.intellij.openapi.module.Module;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An IDEA module's dependency on another IDEA module.
 */
public class ModuleDependency extends Dependency {
  @NotNull private final Module myModule;

  /**
   * Creates a new {@link ModuleDependency}.
   *
   * @throws IllegalArgumentException if the given scope is not supported.
   */
  public ModuleDependency(@NotNull Module module) {
    myModule = module;
  }

  @Nullable
  public Module getModule() {
    return myModule;
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
    return Objects.equals(myModule, that.myModule);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" +
           "module=" + myModule +
           "]";
  }
}
