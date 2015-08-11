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
package com.android.tools.idea.dependencies;

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This class holds methods for checking and adding Android dependencies to a module.
 * For now only Gradle projects are supported, eventually we will need an implementation
 * for both Blaze and legacy IDEA Android projects.
 */
public abstract class DependencyManager {

  /**
   * Returns a {@link DependencyManager} for the specified module.
   */
  @Nullable
  public static DependencyManager getDependencyManager(@NotNull Project project) {
    if (Projects.isBuildWithGradle(project)) {
      return GradleDependencyManager.getInstance(project);
    }
    return null;  // other implementations here
  }

  /**
   * Returns the dependencies that are NOT included in the specified module.
   *
   * @param module the module to check dependencies in
   * @param androidDependencies the dependencies of interest
   * @return a list of the dependencies NOT included in the module
   */
  @NotNull
  public abstract List<String> findMissingDependencies(@NotNull Module module, @NotNull Iterable<String> androidDependencies);

  /**
   * Checks if all the specified dependencies are included in the specified module.
   * <p/>
   * If some dependencies are missing a dialog is presented to the user if those dependencies should be added to the module.
   * If the user agrees the dependencies are added. The caller may supply a callback to determine when the requested dependencies
   * have been added (this make take several seconds).
   *
   * @param module the module to add dependencies to
   * @param androidDependencies the dependencies of interest
   * @param callback an optional callback to signal to completion of the added dependencies
   * @return true if the dependencies were already present in the module, false otherwise
   */
  public abstract boolean ensureLibraryIsIncluded(@NotNull Module module,
                                                  @NotNull Iterable<String> androidDependencies,
                                                  @Nullable Runnable callback);
}
