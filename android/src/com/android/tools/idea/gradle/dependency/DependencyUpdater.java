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
package com.android.tools.idea.gradle.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Updates an IDEA module's dependencies on artifacts (e.g. libraries and other IDEA modules.)
 */
public abstract class DependencyUpdater<T> {
  public final void updateDependencies(@NotNull T module, @NotNull Collection<? extends Dependency> dependencies) {
    for (Dependency dependency : dependencies) {
      if (dependency instanceof LibraryDependency) {
        updateDependency(module, (LibraryDependency)dependency);
      }
      else if (dependency instanceof ModuleDependency) {
        updateDependency(module, (ModuleDependency)dependency);
      }
      else {
        // This will NEVER happen.
        String className = dependency == null ? "null" : dependency.getClass().getName();
        throw new IllegalArgumentException("Unsupported dependency: " + className);
      }
    }
  }

  protected abstract void updateDependency(@NotNull T module, LibraryDependency dependency);

  private void updateDependency(@NotNull T module, ModuleDependency dependency) {
    if (!tryUpdating(module, dependency)) {
      logModuleNotFound(module, dependency);
      // fall back to library dependency, if available.
      LibraryDependency backup = dependency.getBackupDependency();
      if (backup != null) {
        updateDependency(module, backup);
      }
    }
  }

  protected abstract boolean tryUpdating(@NotNull T module, @NotNull ModuleDependency dependency);

  private void logModuleNotFound(@NotNull T module, @NotNull ModuleDependency dependency) {
    String moduleName = getNameOf(module);
    LibraryDependency backup = dependency.getBackupDependency();
    String severity = backup != null ? "Warning" : "Error";
    String category = String.format("%1$s(s) found while populating dependencies of module '%2$s'.", severity, moduleName);
    String msg = String.format("Unable fo find module '%1$s'.", dependency.getName());
    if (backup != null) {
      msg += String.format(" Linking to library '%1$s' instead.", backup.getName());
    }
    log(module, category, msg);
  }

  @NotNull
  protected abstract String getNameOf(@NotNull T module);

  protected abstract void log(T module, String category, String msg);
}
