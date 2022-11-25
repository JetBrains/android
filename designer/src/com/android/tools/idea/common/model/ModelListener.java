/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.model;

import org.jetbrains.annotations.NotNull;

/**
 * Interface implemented by listeners on model changes
 */
public interface ModelListener {
  /**
   * Some of the derived (from layoutlib) data in the model changed. This listener will typically be called after the render has completed.
   *
   * TODO: determine whether the implementors of this actually need this or if they can use modelChanged, indicating that there
   * has been a change to the model itself (e.g. components added or deleted).
   */
  default void modelDerivedDataChanged(@NotNull NlModel model) {}

  /**
   * Something in the model has changed.
   * Note that dependant data (e.g. derived from layoutlib) may not be updated yet.
   */
  default void modelChanged(@NotNull NlModel model) {}

  /**
   * Something in the model has changed "live", but not committed.
   * Listeners may want to schedule a layout pass in reaction to that callback.
   *
   * @param model the notifier model
   * @param animate shall those changes be animated or not
   */
  default void modelLiveUpdate(@NotNull NlModel model, boolean animate) {}

  /** The model changed due to a layout pass */
  default void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {}

  default void modelActivated(@NotNull NlModel model) {}
}