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
package com.android.tools.idea.structure.services;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DeveloperServiceBuildSystemOperations {
  ExtensionPointName<DeveloperServiceBuildSystemOperations> EP_NAME =
    new ExtensionPointName<DeveloperServiceBuildSystemOperations>("com.android.ide.developerServiceBuildSystemOperations");

  boolean canHandle(@NotNull Project project);

  boolean containsAllDependencies(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata);

  boolean isServiceInstalled(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata);

  void removeDependencies(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata);

  void initializeServices(@NotNull Module module, @NotNull final Runnable initializationTask);

  /**
   * @return a unique ID that identifies the build system being used (e.g. "Gradle"). We need this ID because
   * {@link DeveloperServiceCreator} does not have references to projects or modules, which are necessary to identify the build system.
   */
  @NotNull
  String getBuildSystemId();

  /**
   * Given a dependency group ID and artifact ID, e.g. "com.google.android.gms" and "play-services", returns the highest version we know
   * about, or {@code null} if we can't resolve the passed in IDs.
   */
  @Nullable
  String getHighestVersion(@NotNull String groupId, @NotNull String artifactId);
}
