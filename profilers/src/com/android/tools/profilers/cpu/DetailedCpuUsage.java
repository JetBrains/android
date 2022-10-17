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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.UnifiedEventDataSeries;
import org.jetbrains.annotations.NotNull;

public class DetailedCpuUsage extends CpuUsage {

  @NotNull private final RangedContinuousSeries myOtherCpuSeries;
  @NotNull private final RangedContinuousSeries myThreadsCountSeries;
  @NotNull private final Range myThreadRange;

  public DetailedCpuUsage(@NotNull StudioProfilers profilers) {
    super(profilers);

    myThreadRange = new Range(0, 8);

    long streamId = profilers.getSession().getStreamId();
    int pid = profilers.getSession().getPid();
    DataSeries<Long> others = new UnifiedEventDataSeries<>(
      profilers.getClient().getTransportClient(),
      streamId,
      pid,
      Common.Event.Kind.CPU_USAGE,
      pid,
      events -> extractData(events, true));
    DataSeries<Long> threads = new CpuThreadCountDataSeries(profilers.getClient().getTransportClient(), streamId, pid);
    myOtherCpuSeries = new RangedContinuousSeries("Others", profilers.getTimeline().getViewRange(), getCpuRange(), others);
    myThreadsCountSeries = new RangedContinuousSeries("Threads", profilers.getTimeline().getViewRange(), myThreadRange, threads);
    add(myOtherCpuSeries);
    add(myThreadsCountSeries);
  }

  @NotNull
  public RangedContinuousSeries getOtherCpuSeries() {
    return myOtherCpuSeries;
  }

  @NotNull
  public RangedContinuousSeries getThreadsCountSeries() {
    return myThreadsCountSeries;
  }

  @NotNull
  public Range getThreadRange() {
    return myThreadRange;
  }

  @Override
  protected String getCpuSeriesLabel() {
    return "App";
  }
}
