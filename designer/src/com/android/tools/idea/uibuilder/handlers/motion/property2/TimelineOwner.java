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

import org.jetbrains.annotations.NotNull;

/**
 * An interface the timeline component should implement to
 * attach the motion property editor.
 */
public interface TimelineOwner {
  /**
   * A key used to specify a {@link TimelineOwner} on a component.
   */
  String TIMELINE_PROPERTY = "Timeline";

  void addTimelineListener(@NotNull TimelineListener listener);
  void removeTimeLineListener(@NotNull TimelineListener listener);
}
