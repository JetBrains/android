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
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.*;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.proto.Profiler.TimeRequest;
import com.android.tools.profiler.proto.Profiler.TimeResponse;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.openapi.diagnostic.Logger;
import io.grpc.StatusRuntimeException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

  private void importHprof(@NotNull File file) {
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    long startTimestampEpochMs = System.currentTimeMillis();
    long fileCreationTime = TimeUnit.MILLISECONDS.toNanos(startTimestampEpochMs);
    try {
      BasicFileAttributes attributes = Files.readAttributes(Paths.get(file.getPath()), BasicFileAttributes.class);
      fileCreationTime = TimeUnit.MILLISECONDS.toNanos(attributes.creationTime().toMillis());
    }
    catch (IOException e) {
      getLogger().info("File creation time not provided, using system time instead...");
    }

    byte[] bytes;
    try {
      bytes = Files.readAllBytes(Paths.get(file.getPath()));
    }
    catch (IOException e) {
      getLogger().error("Importing Session Failed: cannot read from file location...");
      return;
    }

    // Heap dump and session share a time range of [dumpTimeStamp, dumpTimeStamp + 1) which contains dumpTimestamp as its only integer point.
    Common.Session session = sessionsManager
      .createImportedSession(file.getName(), Common.SessionMetaData.SessionType.MEMORY_CAPTURE, fileCreationTime, fileCreationTime + 1,
                             startTimestampEpochMs);
    // Bind the imported session with heap dump data through MemoryClient.
    HeapDumpInfo heapDumpInfo = HeapDumpInfo.newBuilder()
                                            .setFileName(file.getName())
                                            .setStartTime(fileCreationTime)
                                            .setEndTime(fileCreationTime + 1)
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
      Logger.getInstance(getClass()).error("Importing Session Failed: cannot import heap dump...");
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

    Common.Session session = sessionsManager
      .createImportedSession(file.getName(), Common.SessionMetaData.SessionType.MEMORY_CAPTURE, sessionStartTimeNs, sessionEndTimeNs,
                             startTimestampEpochMs);
    AllocationsInfo info = AllocationsInfo.newBuilder()
                                          .setStartTime(sessionStartTimeNs)
                                          .setEndTime(sessionEndTimeNs)
                                          .setLegacy(true)
                                          .build();
    ImportLegacyAllocationsRequest request = ImportLegacyAllocationsRequest.newBuilder()
                                                                           .setSession(session)
                                                                           .setInfo(info)
                                                                           .setAllocations(LegacyAllocationEventsResponse.newBuilder().setStatus(LegacyAllocationEventsResponse.Status.NOT_READY))
                                                                           .build();
    ImportLegacyAllocationsResponse response = myProfilers.getClient().getMemoryClient().importLegacyAllocations(request);
    // Select the new session
    if (response.getStatus() == ImportLegacyAllocationsResponse.Status.SUCCESS) {
      sessionsManager.update();
      sessionsManager.setSession(session);
    }
    else {
      Logger.getInstance(getClass()).error("Importing Session Failed: cannot import allocation records...");
    }
    myProfilers.getIdeServices().getFeatureTracker().trackCreateSession(Common.SessionMetaData.SessionType.MEMORY_CAPTURE,
                                                                        SessionsManager.SessionCreationSource.MANUAL);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      try {
        LegacyAllocationConverter converter = new LegacyAllocationConverter();
        converter.parseDump(bytes);
        LegacyAllocationEventsResponse allocations = LegacyAllocationEventsResponse.newBuilder()
                                                                                   .setStatus(LegacyAllocationEventsResponse.Status.SUCCESS)
                                                                                   .addAllEvents(converter.getAllocationEvents(info.getStartTime(), info.getEndTime()))
                                                                                   .build();
        AllocationContextsResponse contexts = AllocationContextsResponse.newBuilder()
                                                                        .addAllAllocatedClasses(converter.getClassNames())
                                                                        .addAllAllocationStacks(converter.getAllocationStacks())
                                                                        .build();
        ImportLegacyAllocationsRequest updateRequest = request.toBuilder().setAllocations(allocations).setContexts(contexts).build();
        myProfilers.getClient().getMemoryClient().importLegacyAllocations(updateRequest);
      }
      catch (Exception e) {
        Logger.getInstance(getClass()).error("Importing Session Failed: cannot import allocation records...");

        LegacyAllocationEventsResponse failedResponse = LegacyAllocationEventsResponse
          .newBuilder().setStatus(LegacyAllocationEventsResponse.Status.FAILURE_UNKNOWN).build();
        ImportLegacyAllocationsRequest failedRequest = request.toBuilder().setAllocations(failedResponse).build();
        myProfilers.getClient().getMemoryClient().importLegacyAllocations(failedRequest);
      }
    });
    executorService.shutdown();
  }

  /**
   * @return whether live allocation is active for the specified session. This is determined by whether there are valid
   * {@link AllocationSamplingRateDurationData}'s (which are sent via perfa when live tracking is enabled} within the session's time range.
   */
  static boolean isUsingLiveAllocation(@NotNull StudioProfilers profilers, @NotNull Common.Session session) {
    AllocationSamplingRateDataSeries samplingSeries =
      new AllocationSamplingRateDataSeries(profilers.getClient().getMemoryClient(), session);

    Range dataRange = profilers.getTimeline().getDataRange();
    long rangeMin = TimeUnit.MICROSECONDS.toNanos((long)dataRange.getMin());
    long rangeMax = TimeUnit.MICROSECONDS.toNanos((long)dataRange.getMax());
    List<SeriesData<AllocationSamplingRateDurationData>> series =  samplingSeries.getDataForXRange(new Range(rangeMin, rangeMax));
    return !series.isEmpty();
  }

  /**
   * @return True if live allocation tracking is in FULL mode throughout the entire input time range, false otherwise.
   */
  public static boolean hasOnlyFullAllocationTrackingWithinRegion(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                                                  @NotNull Common.Session session, long startTimeUs, long endTimeUs) {
    AllocationSamplingRateDataSeries series = new AllocationSamplingRateDataSeries(client, session);
    List<SeriesData<AllocationSamplingRateDurationData>> samplingModes = series.getDataForXRange(new Range(startTimeUs, endTimeUs));
    return samplingModes.size() == 1 && samplingModes.get(0).value.getCurrentRateEvent().getSamplingRate().getSamplingNumInterval() ==
                                        MemoryProfilerStage.LiveAllocationSamplingMode.FULL.getValue();
  }

  public static void saveHeapDumpToFile(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                        @NotNull Common.Session session,
                                        @NotNull HeapDumpInfo info,
                                        @NotNull OutputStream outputStream,
                                        @NotNull FeatureTracker featureTracker) {

    DumpDataResponse response = client.getHeapDump(
      DumpDataRequest.newBuilder().setSession(session).setDumpTime(info.getStartTime()).build());
    if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
      try {
        response.getData().writeTo(outputStream);
        featureTracker.trackExportHeap();
      }
      catch (IOException exception) {
        getLogger().warn("Failed to export heap dump file:\n" + exception);
      }
    }
  }

  public static void saveLegacyAllocationToFile(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                                                @NotNull Common.Session session,
                                                @NotNull AllocationsInfo info,
                                                @NotNull OutputStream outputStream,
                                                @NotNull FeatureTracker featureTracker) {
    DumpDataResponse response = client.getLegacyAllocationDump(
      DumpDataRequest.newBuilder().setSession(session).setDumpTime(info.getStartTime()).build());
    if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
      try {
        response.getData().writeTo(outputStream);
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
}
