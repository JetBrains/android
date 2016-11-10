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
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CpuMonitor extends ProfilerMonitor {
  private final int myProcessId;
  private RangedContinuousSeries myRangedSeries;
  private final CpuServiceGrpc.CpuServiceBlockingStub myClient;

  public CpuMonitor(@NotNull StudioProfilers profiler, int pid) {
    myProcessId = pid;
    myClient = profiler.getClient().getCpuClient();
    CpuUsageDataSeries series = new CpuUsageDataSeries(myClient, false, myProcessId);
    myRangedSeries = new RangedContinuousSeries("CPU", profiler.getViewRange(), new Range(0, 100), series);
  }

  @Override
  public RangedContinuousSeries getRangedSeries() {
    return myRangedSeries;
  }

  @Override
  public Stage getExpandedStage(@NotNull StudioProfilers profilers) {
    return new CpuMonitorStage(profilers);
  }
}
