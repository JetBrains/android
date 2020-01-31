/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * Information about the module that will be imported.
 */
public final class ModuleToImport {
  public final String name;
  public final VirtualFile location;
  @NotNull private final Supplier<? extends Iterable<String>> myDependencyComputer;

  /**
   * Creates a new module.
   *
   * @param name               module name
   * @param location           module source location. Can be <code>null</code> if the sources were not found
   * @param dependencyComputer lazily provides a list of modules this module requires.
   */
  public ModuleToImport(@NotNull String name,
                        @Nullable VirtualFile location,
                        @NotNull Supplier<? extends Iterable<String>> dependencyComputer) {
    this.name = name;
    this.location = location;
    myDependencyComputer = Suppliers.memoize(dependencyComputer);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, location);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ModuleToImport) {
      ModuleToImport other = (ModuleToImport)obj;
      return Objects.equal(location, other.location) && Objects.equal(name, other.name);
    }
    else {
      return false;
    }
  }

  @NotNull
  public Iterable<String> getDependencies() {
    Iterable<String> deps = myDependencyComputer.get();
    return deps == null ? Collections.<String>emptySet() : deps;
  }
}
