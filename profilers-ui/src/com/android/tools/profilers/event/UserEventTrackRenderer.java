/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static icons.StudioIcons.Profiler.Events.ROTATE_EVENT;

import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.eventrenderer.EventIconRenderer;
import com.android.tools.adtui.eventrenderer.EventRenderer;
import com.android.tools.adtui.eventrenderer.KeyboardEventRenderer;
import com.android.tools.adtui.eventrenderer.TouchEventRenderer;
import com.android.tools.adtui.model.event.EventModel;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.adtui.model.trackgroup.TrackModel;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.profilers.ProfilerTrackRendererType;
import com.google.common.collect.ImmutableMap;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Track renderer for user interaction events, e.g. touch.
 */
public class UserEventTrackRenderer implements TrackRenderer<EventModel<UserEvent>, ProfilerTrackRendererType> {
  private static final ImmutableMap<UserEvent, EventRenderer<UserEvent>> RENDERERS = ImmutableMap.of(
    UserEvent.TOUCH, new TouchEventRenderer<>(),
    UserEvent.ROTATION, new EventIconRenderer<>(ROTATE_EVENT),
    UserEvent.KEYBOARD, new KeyboardEventRenderer<>()
  );

  @NotNull
  @Override
  public JComponent render(@NotNull TrackModel<EventModel<UserEvent>, ProfilerTrackRendererType> trackModel) {
    return new EventComponent<>(trackModel.getDataModel(), RENDERERS);
  }
}
