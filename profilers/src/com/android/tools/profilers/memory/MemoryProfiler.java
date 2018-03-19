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
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.TimeRequest;
import com.android.tools.profiler.proto.Profiler.TimeResponse;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MemoryProfiler extends StudioProfiler {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfiler.class);
  }

  @NotNull private final AspectObserver myAspectObserver = new AspectObserver();

  public MemoryProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProfilers.addDependency(myAspectObserver).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);

    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    sessionsManager.registerImportHandler("hprof", file -> {
      byte[] bytes;
      try {
        bytes = Files.readAllBytes(Paths.get(file.getPath()));
      }
      catch (IOException e) {
        getLogger().error("Importing Session Failed: can not read from file location...");
        return;
      }
      long dumpTimeStamp = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());

      // Heap dump and session share a time range of [dumpTimeStamp, dumpTimeStamp + 1) which contains dumpTimestamp as its only integer point.
      Common.Session session = sessionsManager
        .createImportedSession(file.getName(), Common.SessionMetaData.SessionType.MEMORY_CAPTURE, dumpTimeStamp, dumpTimeStamp + 1,
                               TimeUnit.NANOSECONDS.toMillis(dumpTimeStamp));
      // Bind the imported session with heap dump data through MemoryClient.
      HeapDumpInfo heapDumpInfo = HeapDumpInfo.newBuilder()
        .setFileName(file.getName())
        .setStartTime(dumpTimeStamp)
        .setEndTime(dumpTimeStamp + 1)
        .build();
      ImportHeapDumpRequest heapDumpRequest = ImportHeapDumpRequest.newBuilder()
        .setSession(session)
        .setData(ByteString.copyFrom(bytes))
        .setInfo(heapDumpInfo)
        .build();
      ImportHeapDumpResponse response = myProfilers.getClient().getMemoryClient().importHeapDump(heapDumpRequest);
      // Select the new session
      if (response.getStatus() == ImportHeapDumpResponse.Status.SUCCESS) {
        sessionsManager.update();
        sessionsManager.setSession(session);
      }
      else {
        Logger.getInstance(getClass()).error("Importing Session Failed: can not import heap dump...");
      }
    });

    myProfilers.registerSessionChangeListener(Common.SessionMetaData.SessionType.MEMORY_CAPTURE,
                                              () -> {
                                                MemoryProfilerStage stage = new MemoryProfilerStage(myProfilers);
                                                myProfilers.setStage(stage);
                                                stage.setPendingCaptureStartTime(myProfilers.getSession().getStartTimestamp());
                                                ProfilerTimeline timeline = myProfilers.getTimeline();
                                                timeline.reset(myProfilers.getSession().getStartTimestamp(),
                                                               myProfilers.getSession().getEndTimestamp());
                                                timeline.getViewRange().set(timeline.getDataRange());
                                                timeline.setIsPaused(true);
                                              });
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new MemoryMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session) {
    myProfilers.getClient().getMemoryClient().startMonitoringApp(MemoryStartRequest.newBuilder().setSession(session).build());
  }

  @Override
  public void stopProfiling(Common.Session session) {
    try {
      Profiler.GetSessionMetaDataResponse response = myProfilers.getClient().getProfilerClient()
        .getSessionMetaData(Profiler.GetSessionMetaDataRequest.newBuilder().setSessionId(session.getSessionId()).build());
      // Only stops live tracking if one is available.
      if (response.getData().getLiveAllocationEnabled() && isUsingLiveAllocation(myProfilers, session)) {
        myProfilers.getClient().getMemoryClient()
          .trackAllocations(TrackAllocationsRequest.newBuilder().setSession(session).setEnabled(false).build());
      }
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }

    myProfilers.getClient().getMemoryClient().stopMonitoringApp(MemoryStopRequest.newBuilder().setSession(session).build());
  }

  /**
   * Attempts to start live allocation tracking.
   */
  private void agentStatusChanged() {
    Common.Session session = myProfilers.getSession();
    if (Common.Session.getDefaultInstance().equals(session) || session.getEndTimestamp() != Long.MAX_VALUE) {
      // Early return if the session is not valid/alive.
      return;
    }

    Profiler.GetSessionMetaDataResponse response = myProfilers.getClient().getProfilerClient()
      .getSessionMetaData(Profiler.GetSessionMetaDataRequest.newBuilder().setSessionId(session.getSessionId()).build());
    if (!response.getData().getLiveAllocationEnabled()) {
      // Early return if live allocation is not enabled for the session.
      return;
    }

    // Only starts live tracking if an existing one is not available.
    if (isUsingLiveAllocation(myProfilers, session)) {
      return;
    }

    if (!myProfilers.isAgentAttached()) {
      // Early return if JVMTI agent is not attached.
      return;
    }

    TimeResponse timeResponse = myProfilers.getClient().getProfilerClient()
      .getCurrentTime(TimeRequest.newBuilder().setDeviceId(session.getDeviceId()).build());
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

  /**
   * @return whether live allocation is active for the specified session.This is determined by whether there is a valid CaptureObject
   * returned by the {@link AllocationInfosDataSeries} within the session's data range.
   */
  static boolean isUsingLiveAllocation(@NotNull StudioProfilers profilers, @NotNull Common.Session session) {
    AllocationInfosDataSeries allocationSeries =
      new AllocationInfosDataSeries(profilers.getClient().getMemoryClient(), session, profilers.getIdeServices().getFeatureTracker(), null);

    Range dataRange = profilers.getTimeline().getDataRange();
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)dataRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)dataRange.getMax());
    List<AllocationsInfo> series = allocationSeries.getInfoForTimeRangeNs(rangeMin, rangeMax);
    if (!series.isEmpty() && !series.get(0).getLegacy()) {
      // There should only be one live allocation capture object.
      assert series.size() == 1;
      return true;
    }

    return false;
  }
}
