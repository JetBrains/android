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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler.TimeRequest;
import com.android.tools.profiler.proto.Profiler.TimeResponse;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

public class MemoryProfiler extends StudioProfiler {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfiler.class);
  }

  @NotNull private final AspectObserver myAspectObserver = new AspectObserver();

  public MemoryProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProfilers.addDependency(myAspectObserver).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new MemoryMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session, Common.Process process) {
    myProfilers.getClient().getMemoryClient().startMonitoringApp(MemoryStartRequest.newBuilder()
                                                                   .setSession(session).build());

    try {
      if (myProfilers.isLiveAllocationEnabled()) {
        myProfilers.getClient().getMemoryClient().resumeTrackAllocations(ResumeTrackAllocationsRequest.newBuilder()
                                                                           .setSession(session).build());
      }
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }
  }

  @Override
  public void stopProfiling(Common.Session session, Common.Process process) {
    try {
      if (myProfilers.isLiveAllocationEnabled()) {
        myProfilers.getClient().getMemoryClient().suspendTrackAllocations(SuspendTrackAllocationsRequest.newBuilder()
                                                                            .setSession(session).build());
      }
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }

    myProfilers.getClient().getMemoryClient().stopMonitoringApp(MemoryStopRequest.newBuilder()
                                                                  .setSession(session).build());
  }

  /**
   * Attempts to start live allocation tracking.
   */
  private void agentStatusChanged() {
    if (!myProfilers.isLiveAllocationEnabled()) {
      return;
    }

    Common.Session session = myProfilers.getSession();
    Common.Device device = myProfilers.getDevice();
    Common.Process process = myProfilers.getProcess();
    if (session == null || process == null) {
      // Early return if no profiling is in session.
      return;
    }

    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(myProfilers.getClient().getMemoryClient(), session,
                                    myProfilers.getTimeline(), myProfilers.getIdeServices().getFeatureTracker(), null);
    // Only starts live tracking if an existing one is not available.
    if (!allocationSeries.getDataForXRange(myProfilers.getTimeline().getDataRange()).isEmpty()) {
      return;
    }

    TimeResponse timeResponse = myProfilers.getClient().getProfilerClient()
      .getCurrentTime(TimeRequest.newBuilder().setDeviceId(device.getDeviceId()).build());
    long timeNs = timeResponse.getTimestampNs();
    try {
      // Attempts to stop an existing tracking session first.
      // This should only happen if we are restarting Studio and reconnecting to an app that already has an agent attached.
      myProfilers.getClient().getMemoryClient().trackAllocations(TrackAllocationsRequest.newBuilder().setRequestTime(timeNs)
                                                                   .setSession(session).setEnabled(false).build());
      myProfilers.getClient().getMemoryClient().trackAllocations(TrackAllocationsRequest.newBuilder().setRequestTime(timeNs)
                                                                   .setSession(session).setEnabled(true).build());
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }
  }
}
