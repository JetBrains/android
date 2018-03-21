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

import com.android.tools.profilers.ProfilerTooltip;
import org.jetbrains.annotations.NotNull;

public class CpuUsageTooltip implements ProfilerTooltip {
  @NotNull private final CpuProfilerStage myStage;
  @NotNull private final CpuProfilerStage.CpuStageLegends myLegends;

  public CpuUsageTooltip(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myLegends = new CpuProfilerStage.CpuStageLegends(stage.getCpuUsage(), stage.getStudioProfilers().getTimeline().getTooltipRange());
    myStage.getStudioProfilers().getUpdater().register(myLegends);
  }

  @Override
  public void dispose() {
    myStage.getStudioProfilers().getUpdater().unregister(myLegends);
  }

  @NotNull
  public CpuProfilerStage.CpuStageLegends getLegends() {
    return myLegends;
  }
}
