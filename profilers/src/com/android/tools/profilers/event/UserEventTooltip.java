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
package com.android.tools.profilers.event;

import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.UserEvent;
import org.jetbrains.annotations.NotNull;

public class UserEventTooltip implements TooltipModel {
  @NotNull private final Timeline myTimeline;
  @NotNull private final EventModel<UserEvent> myUserEvents;

  public UserEventTooltip(@NotNull Timeline timeline, @NotNull EventModel<UserEvent> userEvents) {
    myTimeline = timeline;
    myUserEvents = userEvents;
  }

  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }

  @NotNull
  public EventModel<UserEvent> getUserEvents() {
    return myUserEvents;
  }
}
