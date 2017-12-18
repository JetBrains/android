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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class DetailedCpuUsage extends CpuUsage {

  @NotNull private final RangedContinuousSeries myOtherCpuSeries;
  @NotNull private final RangedContinuousSeries myThreadsCountSeries;
  @NotNull private final Range myThreadRange;

  public DetailedCpuUsage(@NotNull StudioProfilers profilers) {
    super(profilers);

    myThreadRange = new Range(0, 8);

    CpuUsageDataSeries others = new CpuUsageDataSeries(profilers.getClient().getCpuClient(), true, profilers.getSession());
    myOtherCpuSeries = new RangedContinuousSeries("Others", profilers.getTimeline().getViewRange(), getCpuRange(), others);

    CpuThreadCountDataSeries threads = new CpuThreadCountDataSeries(profilers.getClient().getCpuClient(), profilers.getSession());
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
