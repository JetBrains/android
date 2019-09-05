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
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.poller.TransportEventListener;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Memory.AllocationsInfo;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryProfiler.ImportHeapDumpRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ImportLegacyAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.ListHeapDumpInfosResponse;
import com.android.tools.profiler.proto.MemoryProfiler.ListDumpInfosRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStartRequest;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryStopRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsRequest;
import com.android.tools.profiler.proto.MemoryProfiler.TrackAllocationsResponse;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryProfiler extends StudioProfiler {

  private static Logger getLogger() {
    return Logger.getInstance(MemoryProfiler.class);
  }

  @NotNull private final AspectObserver myAspectObserver = new AspectObserver();

  public MemoryProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
    myProfilers.addDependency(myAspectObserver).onChange(ProfilerAspect.AGENT, this::agentStatusChanged);

    SessionsManager sessionsManager = myProfilers.getSessionsManager();

    sessionsManager.registerImportHandler("hprof", this::importHprof);
    sessionsManager.registerImportHandler("alloc", this::importLegacyAllocations);

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
        trackAllocations(myProfilers, session, false, null);
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

    if (!myProfilers.getSessionsManager().getSelectedSessionMetaData().getLiveAllocationEnabled()) {
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

    try {
      // Attempts to stop an existing tracking session first.
      // This should only happen if we are restarting Studio and reconnecting to an app that already has an agent attached.
      trackAllocations(myProfilers, session, false, null);
      trackAllocations(myProfilers, session, true, null);
    }
    catch (StatusRuntimeException e) {
      getLogger().info(e);
    }
  }

  private void importHprof(@NotNull File file) {
    long startTimestampEpochMs = System.currentTimeMillis();
    long sessionStartTimeNs = TimeUnit.MILLISECONDS.toNanos(startTimestampEpochMs);
    // We don't really care about the session having a duration - arbitrarily create a 1-ns session.
    long sessionEndTimeNs = sessionStartTimeNs + 1;
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(Paths.get(file.getPath()));
    }
    catch (IOException e) {
      getLogger().error(String.format("Importing Session Failed: cannot read from %s.", file.getPath()));
      return;
    }

    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    // Bind the imported session with heap dump data through MemoryClient.
    HeapDumpInfo heapDumpInfo = HeapDumpInfo.newBuilder()
      .setStartTime(sessionStartTimeNs)
      .setEndTime(sessionEndTimeNs)
      .build();

    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Common.Event heapDumpEvent = Common.Event.newBuilder()
        .setKind(Common.Event.Kind.MEMORY_HEAP_DUMP)
        .setGroupId(heapDumpInfo.getStartTime())
        .setTimestamp(heapDumpInfo.getStartTime())
        .setIsEnded(true)
        .setMemoryHeapdump(Memory.MemoryHeapDumpData.newBuilder().setInfo(heapDumpInfo))
        .build();
      sessionsManager.createImportedSession(file.getName(),
                                            Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE, sessionStartTimeNs,
                                            sessionEndTimeNs,
                                            startTimestampEpochMs,
                                            ImmutableMap.of(Long.toString(sessionStartTimeNs), ByteString.copyFrom(bytes)),
                                            heapDumpEvent);
    }
    else {
      // Heap dump and session share a time range of [dumpTimeStamp, dumpTimeStamp + 1) which contains dumpTimestamp as its only integer point.
      Common.Session session = sessionsManager
        .createImportedSessionLegacy(file.getName(), Common.SessionMetaData.SessionType.MEMORY_CAPTURE, sessionStartTimeNs,
                                     sessionEndTimeNs,
                                     startTimestampEpochMs);
      ImportHeapDumpRequest heapDumpRequest = ImportHeapDumpRequest.newBuilder()
        .setSession(session)
        .setData(ByteString.copyFrom(bytes))
        .setInfo(heapDumpInfo)
        .build();
      myProfilers.getClient().getMemoryClient().importHeapDump(heapDumpRequest);

      // Select the new session
      sessionsManager.update();
      sessionsManager.setSession(session);
    }

    myProfilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.MEMORY_CAPTURE,
                                                                        SessionsManager.SessionCreationSource.MANUAL);
  }

  private void importLegacyAllocations(@NotNull File file) {
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    long startTimestampEpochMs = System.currentTimeMillis();
    long sessionStartTimeNs = TimeUnit.MILLISECONDS.toNanos(startTimestampEpochMs);
    long sessionEndTimeNs = sessionStartTimeNs + 1;
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(Paths.get(file.getPath()));
    }
    catch (IOException e) {
      getLogger().error("Importing Session Failed: cannot read from file location...");
      return;
    }

    AllocationsInfo info = AllocationsInfo.newBuilder()
      .setStartTime(sessionStartTimeNs)
      .setEndTime(sessionEndTimeNs)
      .setLegacy(true)
      .build();
    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Common.Event heapDumpEvent = Common.Event.newBuilder()
        .setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setGroupId(info.getStartTime())
        .setTimestamp(info.getStartTime())
        .setIsEnded(true)
        .setMemoryAllocTracking(Memory.MemoryAllocTrackingData.newBuilder().setInfo(info))
        .build();
      sessionsManager.createImportedSession(file.getName(),
                                            Common.SessionData.SessionStarted.SessionType.MEMORY_CAPTURE, sessionStartTimeNs,
                                            sessionEndTimeNs,
                                            startTimestampEpochMs,
                                            ImmutableMap.of(Long.toString(sessionStartTimeNs), ByteString.copyFrom(bytes)),
                                            heapDumpEvent);
    }
    else {
      Common.Session session = sessionsManager
        .createImportedSessionLegacy(file.getName(), Common.SessionMetaData.SessionType.MEMORY_CAPTURE, sessionStartTimeNs,
                                     sessionEndTimeNs,
                                     startTimestampEpochMs);
      ImportLegacyAllocationsRequest request = ImportLegacyAllocationsRequest.newBuilder()
        .setSession(session)
        .setInfo(info)
        .setData(ByteString.copyFrom(bytes))
        .build();
      myProfilers.getClient().getMemoryClient().importLegacyAllocations(request);

      // Select the new session
      sessionsManager.update();
      sessionsManager.setSession(session);
    }
    myProfilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.MEMORY_CAPTURE,
                                                                        SessionsManager.SessionCreationSource.MANUAL);
  }

  /**
   * @return whether live allocation is active for the specified session. This is determined by whether there are valid
   * {@link AllocationSamplingRateDurationData}'s (which are sent via perfa when live tracking is enabled} within the session's time range.
   */
  static boolean isUsingLiveAllocation(@NotNull StudioProfilers profilers, @NotNull Common.Session session) {
    AllocationSamplingRateDataSeries samplingSeries =
      new AllocationSamplingRateDataSeries(profilers.getClient(),
                                           session,
                                           profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled());
    return !samplingSeries.getDataForRange(profilers.getTimeline().getDataRange()).isEmpty();
  }

  /**
   * @return True if live allocation tracking is in FULL mode throughout the entire input time range, false otherwise.
   */
  public static boolean hasOnlyFullAllocationTrackingWithinRegion(@NotNull StudioProfilers profilers,
                                                                  @NotNull Common.Session session, long startTimeUs, long endTimeUs) {
    AllocationSamplingRateDataSeries series =
      new AllocationSamplingRateDataSeries(profilers.getClient(),
                                           session,
                                           profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled());
    List<SeriesData<AllocationSamplingRateDurationData>> samplingModes = series.getDataForRange(new Range(startTimeUs, endTimeUs));
    return samplingModes.size() == 1 && samplingModes.get(0).value.getCurrentRate().getSamplingNumInterval() ==
                                        MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue();
  }

  public static void saveHeapDumpToFile(@NotNull ProfilerClient client,
                                        @NotNull Common.Session session,
                                        @NotNull HeapDumpInfo info,
                                        @NotNull OutputStream outputStream,
                                        @NotNull FeatureTracker featureTracker) {
    Transport.BytesResponse response = client.getTransportClient()
      .getBytes(Transport.BytesRequest.newBuilder().setStreamId(session.getStreamId()).setId(Long.toString(info.getStartTime())).build());
    if (response.getContents() != ByteString.EMPTY) {
      try {
        response.getContents().writeTo(outputStream);
        featureTracker.trackExportHeap();
      }
      catch (IOException exception) {
        getLogger().warn("Failed to export heap dump file:\n" + exception);
      }
    }
  }

  public static void saveLegacyAllocationToFile(@NotNull ProfilerClient client,
                                                @NotNull Common.Session session,
                                                @NotNull AllocationsInfo info,
                                                @NotNull OutputStream outputStream,
                                                @NotNull FeatureTracker featureTracker) {
    Transport.BytesResponse response = client.getTransportClient()
      .getBytes(Transport.BytesRequest.newBuilder().setStreamId(session.getStreamId()).setId(Long.toString(info.getStartTime())).build());
    if (response.getContents() != ByteString.EMPTY) {
      try {
        response.getContents().writeTo(outputStream);
        featureTracker.trackExportAllocation();
      }
      catch (IOException exception) {
        getLogger().warn("Failed to export allocation records:\n" + exception);
      }
    }
  }

  /**
   * Generate a default name for a memory capture to be exported. The name suggested is based on the current timestamp and the capture type.
   */
  @NotNull
  static String generateCaptureFileName() {
    StringBuilder builder = new StringBuilder("memory-");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    builder.append(LocalDateTime.now().format(formatter));
    return builder.toString();
  }

  public static List<HeapDumpInfo> getHeapDumpsForSession(@NotNull ProfilerClient client,
                                                          @NotNull Common.Session session,
                                                          @NotNull Range rangeUs,
                                                          @NotNull IdeProfilerServices profilerService) {

    if (profilerService.getFeatureConfig().isUnifiedPipelineEnabled()) {
      Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(session.getStreamId())
        .setPid(session.getPid())
        .setKind(Common.Event.Kind.MEMORY_HEAP_DUMP)
        .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
        .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
        .build();
      Transport.GetEventGroupsResponse response = client.getTransportClient().getEventGroups(request);

      List<HeapDumpInfo> infos = new ArrayList<>();
      for (Transport.EventGroup group : response.getGroupsList()) {
        // We only need the last event to get the most recent HeapDumpInfo
        Common.Event lastEvent = group.getEvents(group.getEventsCount() - 1);
        infos.add(lastEvent.getMemoryHeapdump().getInfo());
      }
      return infos;
    }
    else {
      ListHeapDumpInfosResponse response = client.getMemoryClient().listHeapDumpInfos(
        ListDumpInfosRequest.newBuilder()
          .setSession(session)
          .setStartTime(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
          .setEndTime(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
          .build());

      return response.getInfosList();
    }
  }

  public static List<AllocationsInfo> getAllocationInfosForSession(@NotNull ProfilerClient client,
                                                                   @NotNull Common.Session session,
                                                                   @NotNull Range rangeUs,
                                                                   @NotNull IdeProfilerServices profilerService) {

    if (profilerService.getFeatureConfig().isUnifiedPipelineEnabled()) {
      Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(session.getStreamId())
        .setPid(session.getPid())
        .setKind(Common.Event.Kind.MEMORY_ALLOC_TRACKING)
        .setFromTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
        .setToTimestamp(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
        .build();
      Transport.GetEventGroupsResponse response = client.getTransportClient().getEventGroups(request);

      List<AllocationsInfo> infos = new ArrayList<>();
      for (Transport.EventGroup group : response.getGroupsList()) {
        // We only need the last event to get the most recent HeapDumpInfo
        Common.Event lastEvent = group.getEvents(group.getEventsCount() - 1);
        infos.add(lastEvent.getMemoryAllocTracking().getInfo());
      }
      return infos;
    }
    else {
      MemoryRequest dataRequest = MemoryRequest.newBuilder()
        .setSession(session)
        .setStartTime(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMin()))
        .setEndTime(TimeUnit.MICROSECONDS.toNanos((long)rangeUs.getMax()))
        .build();
      return client.getMemoryClient().getData(dataRequest).getAllocationsInfoList();
    }
  }

  public static void trackAllocations(@NotNull StudioProfilers profilers,
                                      @NotNull Common.Session session,
                                      boolean enable,
                                      @Nullable Consumer<Memory.TrackStatus> responseHandler) {
    TimeResponse timeResponse = profilers.getClient().getTransportClient()
      .getCurrentTime(TimeRequest.newBuilder().setStreamId(session.getStreamId()).build());
    long timeNs = timeResponse.getTimestampNs();

    if (profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
      Commands.Command.Builder trackCommand = Commands.Command.newBuilder()
        .setStreamId(session.getStreamId())
        .setPid(session.getPid());
      if (enable) {
        trackCommand
          .setType(Commands.Command.CommandType.START_ALLOC_TRACKING)
          .setStartAllocTracking(Memory.StartAllocTracking.newBuilder().setRequestTime(timeNs));
      }
      else {
        trackCommand
          .setType(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
          .setStopAllocTracking(Memory.StopAllocTracking.newBuilder().setRequestTime(timeNs));
      }
      Transport.ExecuteResponse response = profilers.getClient().getTransportClient().execute(
        Transport.ExecuteRequest.newBuilder().setCommand(trackCommand).build());
      if (responseHandler != null) {
        TransportEventListener statusListener = new TransportEventListener(Common.Event.Kind.MEMORY_ALLOC_TRACKING_STATUS,
                                                                           profilers.getIdeServices().getMainExecutor(),
                                                                           event -> event.getCommandId() == response.getCommandId(),
                                                                           () -> session.getStreamId(),
                                                                           () -> session.getPid(),
                                                                           event -> {
                                                                             responseHandler.accept(
                                                                               event.getMemoryAllocTrackingStatus().getStatus());
                                                                             // unregisters the listener.
                                                                             return true;
                                                                           });
        profilers.getTransportPoller().registerListener(statusListener);
      }
    }
    else {
      TrackAllocationsResponse response = profilers.getClient().getMemoryClient().trackAllocations(
        TrackAllocationsRequest.newBuilder().setSession(session).setRequestTime(timeNs).setEnabled(enable).build());
      if (responseHandler != null) {
        responseHandler.accept(response.getStatus());
      }
    }
  }
}
