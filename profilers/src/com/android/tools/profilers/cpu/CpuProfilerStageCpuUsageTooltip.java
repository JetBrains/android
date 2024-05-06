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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.TooltipModel;
import org.jetbrains.annotations.NotNull;

/**
 * Tooltip model for displaying CPU usage in {@link CpuProfilerStage}.
 * <p>
 * In addition to CPU usage, this also displays meta data about a CPU recording if present.
 */
public class CpuProfilerStageCpuUsageTooltip implements TooltipModel {
  @NotNull private final CpuProfilerStage.CpuStageLegends myLegends;
  @NotNull private final RangeSelectionModel myRangeSelectionModel;
  @NotNull private final DurationDataModel<CpuTraceInfo> myTraceDurations;

  public CpuProfilerStageCpuUsageTooltip(@NotNull CpuProfilerStage stage) {
    this(new CpuProfilerStage.CpuStageLegends(stage.getCpuUsage(), stage.getStudioProfilers().getTimeline().getTooltipRange()),
         stage.getRangeSelectionModel(), stage.getTraceDurations());
  }

  // Overloaded constructor that doesn't take in stage for usage in live view
  public CpuProfilerStageCpuUsageTooltip(@NotNull CpuProfilerStage.CpuStageLegends cpuStageLegends,
                                         @NotNull RangeSelectionModel rangeSelectionModel,
                                         @NotNull DurationDataModel<CpuTraceInfo> traceDurations) {
    myLegends = cpuStageLegends;
    myRangeSelectionModel = rangeSelectionModel;
    myTraceDurations = traceDurations;
  }

  @NotNull
  public CpuProfilerStage.CpuStageLegends getLegends() {
    return myLegends;
  }

  /**
   * @return the range selection model for the view to display info differently based on selection state.
   */
  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  /**
   * @return all recorded trace duration data.
   */
  @NotNull
  public DurationDataModel<CpuTraceInfo> getTraceDurations() {
    return myTraceDurations;
  }
}
