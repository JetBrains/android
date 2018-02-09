/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant.datamodel;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulation of analytics events that occur inside of the Assistant
 * infrastructure code.
 *
 * All methods are no-op defaults as implementers may only need a subset
 * of the events to be tracked.
 *
 * NOTE: Tracking for button clicks has been left out as these result in plugin code execution
 * and may be tracked directly in the plugin.
 */
public interface AnalyticsProvider {

  /**
   * Called when your implementation of the Assistant panel has been opened.
   *
   * @param project The project context.
   */
  @SuppressWarnings("unused")
  default void trackPanelOpened(@NotNull Project project) {
  }

  /**
   * Called when someone expands a feature grouping to see the tutorial listing.
   *
   * @param project The project context.
   */
  @SuppressWarnings("unused")
  default void trackFeatureGroupExpanded(@NotNull String groupName, @NotNull Project project) {
  }

  /**
   * Tracks a tutorial being opened. Note that, if there is only one feature/tutorial for the
   * entire plugin, it will be opened by default and will register as a user action.
   *
   * @param tutorialId The id of the tutorial being opened.
   * @param project    The project context.
   */
  @SuppressWarnings("unused")
  default void trackTutorialOpen(@NotNull String tutorialId, @NotNull Project project) {
  }

  /**
   * Tracks a tutorial being closed, either by navigation away or the panel being closed.
   * Note that some edge cases may not be covered (such as a crash closing Studio) and
   * your close events may lag behind the number of open events you see.
   *
   * Currently we do not track tutorials being closed via the Assistant or Studio being
   * closed. This should be corrected in the future.
   *
   * @param tutorialId The id of the tutorial being closed.
   * @param timeOpenMs The time, in ms, that the viewer had the tutorial open.
   * @param project    The project context.
   */
  @SuppressWarnings("unused")
  default void trackTutorialClosed(@NotNull String tutorialId, long timeOpenMs, @NotNull Project project) {
  }

  /**
   * NoOp singleton implementation.
   */
  enum AnalyticsProviderNoOp implements AnalyticsProvider {
    INSTANCE
  }

  /**
   * Returns no-op singleton used for convenience in {@code AssistantBundleCreator} so that parties
   * who do not wish to use analytics still return a non-null object.
   */
  static AnalyticsProvider getNoOp() {
    return AnalyticsProviderNoOp.INSTANCE;
  }
}
