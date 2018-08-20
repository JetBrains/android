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
package com.android.tools.idea.gradle.project.sync;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface GradleSyncListener extends EventListener {
  /**
   * Invoked when sync task has been created and sync is going to happen some time soon. Useful in situations when
   * sync gets executed asynchronously so syncStarted() isn't called yet but it's necessary to know that sync is
   * imminent.
   *
   * @param project Project which sync has been requested for.
   * @param request Sync request to be fulfilled.
   */
  default void syncTaskCreated(@NotNull Project project, @NotNull GradleSyncInvoker.Request request) {
  }

  default void syncStarted(@NotNull Project project, boolean skipped, boolean sourceGenerationRequested) {
  }

  default void setupStarted(@NotNull Project project) {
  }

  /**
   * Invoked when a Gradle project has been synced. It is not guaranteed that the created IDEA project has been compiled.
   *
   * @param project the IDEA project created from the Gradle one.
   */
  default void syncSucceeded(@NotNull Project project) {
  }

  default void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
  }

  /**
   * Invoked when the state of a project has been loaded from a disk cache, instead of syncing with Gradle.
   *
   * @param project the project.
   */
  default void syncSkipped(@NotNull Project project) {
  }

  default void sourceGenerationFinished(@NotNull Project project) {
  }
}
