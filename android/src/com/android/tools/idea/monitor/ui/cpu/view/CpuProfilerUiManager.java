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
package com.android.tools.idea.monitor.ui.cpu.view;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.model.CpuDataPoller;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuProfilerServiceGrpc;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class CpuProfilerUiManager extends BaseProfilerUiManager {

  @NotNull
  private final CpuDataPoller myCpuDataPoller;

  private BaseSegment myThreadSegment;

  public CpuProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                       @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, datastore, eventDispatcher);
    myCpuDataPoller = new CpuDataPoller();
  }

  @Override
  public void startMonitoring(int pid) {
    CpuProfilerServiceGrpc.CpuProfilerServiceBlockingStub cpuService = myDataStore.getDeviceProfilerService().getCpuService();
    CpuProfiler.CpuStartRequest.Builder requestBuilder = CpuProfiler.CpuStartRequest.newBuilder().setAppId(pid);
    cpuService.startMonitoringApp(requestBuilder.build());
    myCpuDataPoller.startDataRequest(pid, cpuService);
  }

  @Override
  public void stopMonitoring(int pid) {
    myCpuDataPoller.stopDataRequest();
    CpuProfiler.CpuStopRequest.Builder requestBuilder = CpuProfiler.CpuStopRequest.newBuilder();
    requestBuilder.setAppId(pid);
    myDataStore.getDeviceProfilerService().getCpuService().stopMonitoringApp(requestBuilder.build());
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(overviewPanel);

    myThreadSegment = new ThreadsSegment(myXRange, myDataStore, myEventDispatcher, null);
    setupAndRegisterSegment(myThreadSegment);
    overviewPanel.add(myThreadSegment);
    setSegmentState(overviewPanel, myThreadSegment, AccordionLayout.AccordionState.MAXIMIZE);
  }

  @Override
  public void resetProfiler(@NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(overviewPanel, detailPanel);

    // TODO un-register thread segment components from choreographer
    overviewPanel.remove(myThreadSegment);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new CpuUsageSegment(xRange, dataStore, eventDispatcher);
  }
}
