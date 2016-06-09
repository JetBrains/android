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
package com.android.tools.idea.editors.gfxtrace;

import com.android.tools.idea.stats.UsageTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GfxTraceUtil {

  /**
   * more info here: https://support.google.com/analytics/answer/1033068
   *
   * @param eventLabel With labels, you can provide additional information for events that you want to track, such as the movie title in
   *                   the video examples, or the name of a file when tracking downloads.
   * @param eventValue Use it to assign a numerical value to a tracked page object. For example, you could use it to provide the time in
   *                   seconds for an player to load, or you might trigger a dollar value when a specific playback marker is reached on a
   *                   video player.
   */
  public static void trackEvent(@NotNull String eventAction, @Nullable String eventLabel, @Nullable Integer eventValue) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_GFX_TRACE, eventAction, eventLabel, eventValue);
  }
}
