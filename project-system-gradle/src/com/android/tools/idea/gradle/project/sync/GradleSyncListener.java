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
import java.util.EventListener;
import org.jetbrains.annotations.NotNull;

public interface GradleSyncListener extends EventListener {

  /**
   * Invoked when a Gradle project sync starts. This could be due to many reasons.
   *
   * NOTE: This notification is not delivered to direct listeners via {@link GradleSyncInvoker#sync}.
   **/
  default void syncStarted(@NotNull Project project) {
  }

  /**
   * Invoked when the a Gradle project sync has failed. This could be due to many reasons.
   *
   * @param project the IDEA project created from the Gradle one,  classes that implement this method must deal with
   *    *           the case where the project has been disposed, this can be checked by calling {@link Project#isDisposed()}.
   * @param errorMessage the error message that the Sync failed with.
   */
  default void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
  }

  /**
   * Invoked when a Gradle project has been synced. It is not guaranteed that the created IDEA project has been compiled.
   *
   * @param project the IDEA project created from the Gradle one, classes that implement this method must deal with
   *                the case where the project has been disposed, this can be checked by calling {@link Project#isDisposed()}.
   */
  default void syncSucceeded(@NotNull Project project) {
  }

  /**
   * Invoked when the state of a project has been loaded from a disk cache, instead of syncing with Gradle.
   *
   * @param project the project, classes that implement this method must deal with
   *    *           the case where the project has been disposed, this can be checked by calling {@link Project#isDisposed()}.
   */
  default void syncSkipped(@NotNull Project project) {
  }

  /**
   * Invoked when sync was cancelled.
   */
  default void syncCancelled(@NotNull Project project) {
    // Each sync must finish with one of these events and since this method is added later we need to re-interpret it as a different already
    // known sync outcome.
    syncFailed(project, "Cancelled");
  }
}
