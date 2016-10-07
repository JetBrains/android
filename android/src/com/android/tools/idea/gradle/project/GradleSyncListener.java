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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface GradleSyncListener extends EventListener {
  void syncStarted(@NotNull Project project);

  /**
   * Invoked when a Gradle project has been synced. It is not guaranteed that the created IDEA project has been compiled.
   *
   * @param project the IDEA project created from the Gradle one.
   */
  void syncSucceeded(@NotNull Project project);

  void syncFailed(@NotNull Project project, @NotNull String errorMessage);

  /**
   * Invoked when the state of a project has been loaded from a disk cache, instead of syncing with Gradle.
   *
   * @param project the project.
   */
  void syncSkipped(@NotNull Project project);

  abstract class Adapter implements GradleSyncListener {
    @Override
    public void syncStarted(@NotNull Project project) {
    }

    @Override
    public void syncSucceeded(@NotNull Project project) {
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    }

    @Override
    public void syncSkipped(@NotNull Project project) {
    }
  }
}
