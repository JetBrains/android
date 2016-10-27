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
package com.android.tools.profilers;

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.CpuUsageDataSeries;

public class StudioMonitor extends StudioProfilerStage {
  private final StudioProfiler myProfiler;
  private RangedContinuousSeries myRangedSeries;
  private int myProcessId;

  public StudioMonitor(StudioProfiler profiler) {
    myProfiler = profiler;
  }

  @Override
  public void enter() {
    // TODO: Move this to a CPU specific class, and generalize multiple monitors
    ProfilerClient client = myProfiler.getClient();
    myProcessId = myProfiler.getProcessId();
    client.getCpuClient().startMonitoringApp(CpuProfiler.CpuStartRequest.newBuilder().setAppId(myProcessId).build());
    CpuUsageDataSeries series = new CpuUsageDataSeries(client, false, myProcessId);
    myRangedSeries = new RangedContinuousSeries("CPU", myProfiler.getViewRange(), new Range(0, 100), series);
  }

  @Override
  public void exit() {
    ProfilerClient client = myProfiler.getClient();
    client.getCpuClient().stopMonitoringApp(CpuProfiler.CpuStopRequest.newBuilder().setAppId(myProcessId).build());
  }

  public RangedContinuousSeries getRangedSeries() {
    return myRangedSeries;
  }
}
