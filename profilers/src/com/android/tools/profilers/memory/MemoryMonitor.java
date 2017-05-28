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
package com.android.tools.profilers.memory;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.AxisComponentModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

public class MemoryMonitor extends ProfilerMonitor {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryMonitor.class);
  }

  @NotNull
  private final AxisComponentModel myMemoryAxis;

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 2, 5);
  private final MemoryUsage myMemoryUsage;
  private final MemoryLegend myMemoryLegend;
  private MemoryLegend myTooltipLegend;

  public MemoryMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
    myMemoryUsage = new MemoryUsage(profilers);

    myMemoryAxis = new AxisComponentModel(myMemoryUsage.getMemoryRange(), MEMORY_AXIS_FORMATTER);
    myMemoryAxis.setClampToMajorTicks(true);

    myMemoryLegend = new MemoryLegend(myMemoryUsage, getTimeline().getDataRange(), LEGEND_UPDATE_FREQUENCY_MS);
    myTooltipLegend = new MemoryLegend(myMemoryUsage, getTimeline().getTooltipRange(), 0);

    myProfilers.addDependency(this).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
    agentStatusChanged();
  }

  @Override
  public String getName() {
    return "MEMORY";
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myMemoryUsage);
    myProfilers.getUpdater().register(myMemoryAxis);
    myProfilers.getUpdater().register(myMemoryLegend);
    myProfilers.getUpdater().register(myTooltipLegend);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myMemoryUsage);
    myProfilers.getUpdater().unregister(myMemoryAxis);
    myProfilers.getUpdater().unregister(myMemoryLegend);
    myProfilers.getUpdater().unregister(myTooltipLegend);
    myProfilers.removeDependencies(this);
  }

  private void agentStatusChanged() {
    if (!myProfilers.isAgentAttached()) {
      return;
    }

    startLiveAllocationTracking();
  }

  /**
   * Attempts to start live allocation tracking.
   * TODO: currently this repeated tries to start live tracking whenever users returns to the L1 view. While restarting tracking is a
   * no-op in perfd+agent, we should have a way to get the current tracking status first.
   */
  private void startLiveAllocationTracking() {
    if (!(myProfilers.getIdeServices().getFeatureConfig().isLiveAllocationsEnabled() &&
          myProfilers.getDevice().getFeatureLevel() >= AndroidVersion.VersionCodes.O)) {
      // no-op for pre-O device or if live allocation tracking flag is not on.
      return;
    }

    Common.Session session = myProfilers.getSession();
    Profiler.Process process = myProfilers.getProcess();
    assert session != null;
    assert process != null;
    Profiler.TimeResponse timeResponse = myProfilers.getClient().getProfilerClient()
      .getCurrentTime(Profiler.TimeRequest.newBuilder().setSession(session).build());
    long timeNs = timeResponse.getTimestampNs();

    try {
      com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse response = myProfilers.getClient().getMemoryClient()
        .trackAllocations(MemoryProfiler.TrackAllocationsRequest.newBuilder().setRequestTime(timeNs)
                            .setSession(session).setProcessId(process.getPid())
                            .setEnabled(true).build());
      switch (response.getStatus()) {
        case IN_PROGRESS:
          getLogger().info("Allocation tracking is already enabled.");
          break;
        default:
          break;
      }
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }
  }

  @Override
  public void expand() {
    myProfilers.setStage(new MemoryProfilerStage(myProfilers));
  }

  public AxisComponentModel getMemoryAxis() {
    return myMemoryAxis;
  }

  public MemoryUsage getMemoryUsage() {
    return myMemoryUsage;
  }

  public MemoryLegend getMemoryLegend() {
    return myMemoryLegend;
  }

  public MemoryLegend getTooltipLegend() {
    return myTooltipLegend;
  }

  public static class MemoryLegend extends LegendComponentModel {

    @NotNull
    private final SeriesLegend myTotalLegend;

    public MemoryLegend(@NotNull MemoryUsage usage, @NotNull Range range, int updateFrequencyMs) {
      super(updateFrequencyMs);
      myTotalLegend = new SeriesLegend(usage.getTotalMemorySeries(), MEMORY_AXIS_FORMATTER, range);
      add(myTotalLegend);
    }

    @NotNull
    public Legend getTotalLegend() {
      return myTotalLegend;
    }
  }
}