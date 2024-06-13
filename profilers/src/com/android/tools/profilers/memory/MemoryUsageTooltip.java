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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.TooltipModel;
import org.jetbrains.annotations.NotNull;

public class MemoryUsageTooltip implements TooltipModel {
  @NotNull private final MemoryStageLegends myMemoryStageToolTipLegends;
  @NotNull private final Boolean myIsLiveAllocationTrackingReady;

  MemoryUsageTooltip(@NotNull BaseStreamingMemoryProfilerStage stage) {
    this(stage.getTooltipLegends(), stage.isLiveAllocationTrackingReady());
  }

  MemoryUsageTooltip(@NotNull MemoryStageLegends memoryStageLegends,
                     @NotNull Boolean isLiveAllocationTrackingReady) {
    myMemoryStageToolTipLegends = memoryStageLegends;
    myIsLiveAllocationTrackingReady = isLiveAllocationTrackingReady;
  }

  public MemoryStageLegends getLegends() {
    return myMemoryStageToolTipLegends;
  }

  public boolean useLiveAllocationTracking() {
    return myIsLiveAllocationTrackingReady;
  }
}
