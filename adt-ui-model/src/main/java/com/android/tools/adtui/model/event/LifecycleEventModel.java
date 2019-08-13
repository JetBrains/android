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
package com.android.tools.adtui.model.event;

import com.android.tools.adtui.model.RangedSeries;
import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle event model, which combines Activity and Fragment data series.
 */
public class LifecycleEventModel extends EventModel<LifecycleEvent> {
  @NotNull
  private final RangedSeries<EventAction<LifecycleEvent>> myFragmentSeries;

  public LifecycleEventModel(@NotNull RangedSeries<EventAction<LifecycleEvent>> activitySeries,
                             @NotNull RangedSeries<EventAction<LifecycleEvent>> fragmentSeries) {
    super(activitySeries);
    myFragmentSeries = fragmentSeries;
  }

  @NotNull
  public RangedSeries<EventAction<LifecycleEvent>> getActivitySeries() {
    return getRangedSeries();
  }

  @NotNull
  public RangedSeries<EventAction<LifecycleEvent>> getFragmentSeries() {
    return myFragmentSeries;
  }
}
