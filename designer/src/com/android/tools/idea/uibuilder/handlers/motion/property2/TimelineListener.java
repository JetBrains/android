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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface used to notify the motion property editor to display data.
 */
public interface TimelineListener {
  /**
   * Display properties related to a transition with a selected component.
   */
  void updateTransition(@NotNull MotionSceneModel.TransitionTag transition, @Nullable NlComponent component);

  /**
   * Display properties of the specified constraintSet for a selected component.
   */
  void updateConstraintSet(@NotNull MotionSceneModel.ConstraintSet constraintSet, @Nullable NlComponent component);

  /**
   * Display properties of the specified key frame of a specified component.
   */
  void updateSelection(@NotNull MotionSceneModel.KeyFrame keyFrame, @Nullable NlComponent component);
}
