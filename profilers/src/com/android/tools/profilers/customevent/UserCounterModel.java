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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Line chart model that tracks the data of user-specified events in Custom Event Visualization.
 */
public class UserCounterModel extends LineChartModel {

  /**
   *  Hashcode of myEventName is used as the event group id for user events.
   */
  @NotNull private final String myEventName;
  @NotNull private final RangedContinuousSeries myUserCounterSeries;
  private static final Range DEFAULT_CUSTOM_EVENT_RANGE = new Range(0, 10);

  public UserCounterModel(@NotNull StudioProfilers profilers, @NotNull String eventName) {
    myEventName = eventName;
    myUserCounterSeries = createRangedSeries(profilers, getSeriesLabel(), DEFAULT_CUSTOM_EVENT_RANGE,
                                             myEventName.hashCode(), UnifiedEventDataSeries
                                               .fromFieldToDataExtractor(e -> (long)e.getUserCounters().getRecordedValue()));

    add(myUserCounterSeries);
  }

  private RangedContinuousSeries createRangedSeries(@NotNull StudioProfilers profilers,
                                                      @NotNull String name,
                                                      @NotNull Range range,
                                                      int groupId, Function<List<Common.Event>, List<SeriesData<Long>>> dataExtractor) {
    TransportServiceGrpc.TransportServiceBlockingStub client = profilers.getClient().getTransportClient();
    UnifiedEventDataSeries<Long> series = new UnifiedEventDataSeries<>(client,
                                                                       profilers.getSession().getStreamId(),
                                                                       profilers.getSession().getPid(),
                                                                       Common.Event.Kind.USER_COUNTERS,
                                                                       groupId,
                                                                       dataExtractor);
    return new RangedContinuousSeries(name, profilers.getTimeline().getViewRange(), range, series, profilers.getTimeline().getDataRange());
  }

  private String getSeriesLabel() {
    return "";
  }

  @NotNull
  @VisibleForTesting
  RangedContinuousSeries getEventSeries() {
    return myUserCounterSeries;
  }

  @NotNull
  @VisibleForTesting
  Range getUsageRange() {
    return DEFAULT_CUSTOM_EVENT_RANGE;
  }
}