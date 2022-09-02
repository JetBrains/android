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
package com.android.tools.idea.profilers.analytics;

import static com.android.ide.common.util.DeviceUtils.isMdnsAutoConnectTls;
import static com.android.ide.common.util.DeviceUtils.isMdnsAutoConnectUnencrypted;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.config.ArtSampledConfiguration;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.energy.EnergyDuration;
import com.android.tools.profilers.memory.HprofSessionArtifact;
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionItem;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AdtUiBoxSelectionMetadata;
import com.google.wireless.android.sdk.stats.AdtUiTrackGroupMetadata;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AppInspectionEvent;
import com.google.wireless.android.sdk.stats.CpuApiTracingMetadata;
import com.google.wireless.android.sdk.stats.CpuCaptureMetadata;
import com.google.wireless.android.sdk.stats.CpuImportTraceMetadata;
import com.google.wireless.android.sdk.stats.CpuProfilingConfig;
import com.google.wireless.android.sdk.stats.CpuStartupProfilingMetadata;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.EnergyEvent;
import com.google.wireless.android.sdk.stats.EnergyEventCount;
import com.google.wireless.android.sdk.stats.EnergyEventMetadata;
import com.google.wireless.android.sdk.stats.EnergyRangeMetadata;
import com.google.wireless.android.sdk.stats.FilterMetadata;
import com.google.wireless.android.sdk.stats.MemoryInstanceFilterMetadata;
import com.google.wireless.android.sdk.stats.ProfilerSessionCreationMetaData;
import com.google.wireless.android.sdk.stats.ProfilerSessionSelectionMetaData;
import com.google.wireless.android.sdk.stats.RunWithProfilingMetadata;
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonManagerStats;
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats;
import com.google.wireless.android.sdk.stats.TransportFailureMetadata;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StudioFeatureTracker implements FeatureTracker {

  @Nullable
  private Common.Device myActiveDevice;

  @Nullable
  private Common.Process myActiveProcess;

  @NotNull
  private final Project myTrackingProject;

  public StudioFeatureTracker(@NotNull Project trackingProject) {
    myTrackingProject = trackingProject;
  }

  private final ImmutableMap<Common.SessionMetaData.SessionType, ProfilerSessionCreationMetaData.SessionType> SESSION_TYPE_MAP =
    ImmutableMap.of(
      Common.SessionMetaData.SessionType.FULL, ProfilerSessionCreationMetaData.SessionType.FULL_SESSION,
      Common.SessionMetaData.SessionType.MEMORY_CAPTURE, ProfilerSessionCreationMetaData.SessionType.MEMORY_CAPTURE,
      Common.SessionMetaData.SessionType.CPU_CAPTURE, ProfilerSessionCreationMetaData.SessionType.CPU_CAPTURE
    );

  private final ImmutableMap<SessionsManager.SessionCreationSource, ProfilerSessionCreationMetaData.CreationSource>
    SESSION_CREATION_SOURCE_MAP =
    ImmutableMap.of(
      SessionsManager.SessionCreationSource.MANUAL, ProfilerSessionCreationMetaData.CreationSource.MANUAL
    );

  private final ImmutableMap<Class<? extends SessionArtifact>, ProfilerSessionSelectionMetaData.ArtifactType> SESSION_ARTIFACT_MAP =
    ImmutableMap.of(
      SessionItem.class, ProfilerSessionSelectionMetaData.ArtifactType.ARTIFACT_SESSION,
      HprofSessionArtifact.class, ProfilerSessionSelectionMetaData.ArtifactType.ARTIFACT_HPROF,
      CpuCaptureSessionArtifact.class, ProfilerSessionSelectionMetaData.ArtifactType.ARTIFACT_CPU_CAPTURE,
      LegacyAllocationsSessionArtifact.class, ProfilerSessionSelectionMetaData.ArtifactType.ARTIFACT_LEGACY_ALLOCATIONS
    );

  private final ImmutableMap<Class<? extends Exception>, TransportFailureMetadata.FailureType> TRANSPORT_FAILURE_MAP =
    ImmutableMap.<Class<? extends Exception>, TransportFailureMetadata.FailureType>builder()
      .put(TimeoutException.class, TransportFailureMetadata.FailureType.TIMEOUT)
      .put(InterruptedException.class, TransportFailureMetadata.FailureType.INTERRUPTED)
      .put(IOException.class, TransportFailureMetadata.FailureType.IO)
      .put(SyncException.class, TransportFailureMetadata.FailureType.SYNC)
      .put(ShellCommandUnresponsiveException.class, TransportFailureMetadata.FailureType.SHELL_COMMAND_UNRESPONSIVE)
      .put(AdbCommandRejectedException.class, TransportFailureMetadata.FailureType.ADB_COMMAND_REJECTED)
      .build();

  private final static ImmutableMap<com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus, CpuCaptureMetadata.CaptureStatus>
    CPU_CAPTURE_STATUS_MAP =
    ImmutableMap.<com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus, CpuCaptureMetadata.CaptureStatus>builder()
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.SUCCESS,
           CpuCaptureMetadata.CaptureStatus.SUCCESS)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILURE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_CAPTURING_FAILURE,
           CpuCaptureMetadata.CaptureStatus.STOP_CAPTURING_FAILURE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING,
           CpuCaptureMetadata.CaptureStatus.USER_ABORTED_PARSING)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE,
           CpuCaptureMetadata.CaptureStatus.PREPROCESS_FAILURE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_NO_GOING_PROFILING,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_NO_GOING_PROFILING)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_APP_PROCESS_DIED,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_APP_PROCESS_DIED)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_APP_PID_CHANGED,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_APP_PID_CHANGED)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_PROFILER_PROCESS_DIED,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_PROFILER_PROCESS_DIED)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STOP_COMMAND_FAILED,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STOP_COMMAND_FAILED)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STILL_PROFILING_AFTER_STOP,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_STILL_PROFILING_AFTER_STOP)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_START_WAITING,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_START_WAITING)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_WAIT_TIMEOUT,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_WAIT_TIMEOUT)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_WAIT_FAILED,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_WAIT_FAILED)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_READ_WAIT_EVENT,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_READ_WAIT_EVENT)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_COPY_FILE,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_COPY_FILE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_FORM_FILE,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_FORM_FILE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_READ_FILE,
           CpuCaptureMetadata.CaptureStatus.STOP_FAILED_CANNOT_READ_FILE)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PATH_INVALID,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PATH_INVALID)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_READ_ERROR,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_READ_ERROR)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_UNKNOWN,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_UNKNOWN)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_FILE_HEADER_ERROR,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_FILE_HEADER_ERROR)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_PARSER_ERROR)
      .put(com.android.tools.profilers.cpu.CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_CAUSE_UNKNOWN,
           CpuCaptureMetadata.CaptureStatus.PARSING_FAILED_CAUSE_UNKNOWN)
      .build();

  private final ImmutableMap<Class<? extends CaptureObjectInstanceFilter>, MemoryInstanceFilterMetadata.FilterType>
    MEMORY_INSTANCE_FILTER_MAP =
    ImmutableMap.<Class<? extends CaptureObjectInstanceFilter>, MemoryInstanceFilterMetadata.FilterType>builder()
      .put(ActivityFragmentLeakInstanceFilter.class, MemoryInstanceFilterMetadata.FilterType.ACTIVITY_FRAGMENT_LEAKS)
      .put(ProjectClassesInstanceFilter.class, MemoryInstanceFilterMetadata.FilterType.PROJECT_CLASSES)
      .build();

  @NotNull
  private AndroidProfilerEvent.Stage myCurrStage = AndroidProfilerEvent.Stage.UNKNOWN_STAGE;

  @Override
  public void trackPreTransportDaemonStarts(@NotNull Common.Device transportDevice) {
    newTracker(AndroidProfilerEvent.Type.PRE_TRANSPORT_DAEMON_STARTS).setDevice(transportDevice).track();
  }

  @Override
  public void trackTransportDaemonFailed(@NotNull Common.Device transportDevice, Exception exception) {
    TransportFailureMetadata.FailureType failureType =
      TRANSPORT_FAILURE_MAP.getOrDefault(exception.getClass(), TransportFailureMetadata.FailureType.UNKNOWN_FAILURE_TYPE);
    newTracker(AndroidProfilerEvent.Type.TRANSPORT_DAEMON_FAILED).setDevice(transportDevice)
      .setTransportFailureMetadata(TransportFailureMetadata.newBuilder().setFailureType(failureType).build()).track();
  }

  @Override
  public void trackTransportProxyCreationFailed(@NotNull Common.Device transportDevice, Exception exception) {
    TransportFailureMetadata.FailureType failureType =
      TRANSPORT_FAILURE_MAP.getOrDefault(exception.getClass(), TransportFailureMetadata.FailureType.UNKNOWN_FAILURE_TYPE);
    newTracker(AndroidProfilerEvent.Type.TRANSPORT_PROXY_FAILED).setDevice(transportDevice)
      .setTransportFailureMetadata(TransportFailureMetadata.newBuilder().setFailureType(failureType).build()).track();
  }

  @Override
  public void trackProfilerInitializationFailed() {
    track(AndroidProfilerEvent.Type.PROFILER_INITIALIZATION_FAILED);
  }

  @Override
  public void trackEnterStage(AndroidProfilerEvent.Stage stageType) {
    myCurrStage = stageType;
    track(AndroidProfilerEvent.Type.STAGE_ENTERED);
  }

  @Override
  public void trackRunWithProfiling(@NotNull RunWithProfilingMetadata metadata) {
    newTracker(AndroidProfilerEvent.Type.RUN_WITH_PROFILING).setRunWithProfilingMetadata(metadata).track();
  }

  @Override
  public void trackAutoProfilingRequested() {
    track(AndroidProfilerEvent.Type.AUTO_PROFILING_REQUESTED);
  }

  @Override
  public void trackAutoProfilingSucceeded() {
    track(AndroidProfilerEvent.Type.AUTO_PROFILING_SUCCEEDED);
  }

  @Override
  public void trackProfilingStarted() {
    newTracker(AndroidProfilerEvent.Type.PROFILING_STARTED).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackAdvancedProfilingStarted() {
    newTracker(AndroidProfilerEvent.Type.ADVANCED_PROFILING_STARTED).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackChangeDevice(@Nullable Common.Device device) {
    if (myActiveDevice != device) {
      myActiveDevice = device;
      newTracker(AndroidProfilerEvent.Type.CHANGE_DEVICE).setDevice(myActiveDevice).track();
    }
  }

  @Override
  public void trackChangeProcess(@Nullable Common.Process process) {
    if (myActiveProcess != process) {
      myActiveProcess = process;
      newTracker(AndroidProfilerEvent.Type.CHANGE_PROCESS).setDevice(myActiveDevice).track();
    }
  }

  @Override
  public void trackSessionDropdownClicked() {
    track(AndroidProfilerEvent.Type.SESSION_DROPDOWN_CLICKED);
  }

  @Override
  public void trackCreateSession(Common.SessionMetaData.SessionType sessionType, SessionsManager.SessionCreationSource sourceType) {
    ProfilerSessionCreationMetaData.Builder builder = ProfilerSessionCreationMetaData.newBuilder()
      .setCreatedType(SESSION_TYPE_MAP.getOrDefault(sessionType, ProfilerSessionCreationMetaData.SessionType.UNKNOWN_SESSION))
      .setCreationSource(
        SESSION_CREATION_SOURCE_MAP.getOrDefault(sourceType, ProfilerSessionCreationMetaData.CreationSource.UNKNOWN_SOURCE));
    newTracker(AndroidProfilerEvent.Type.SESSION_CREATED).setSessionCreationMetadata(builder.build()).track();
  }

  @Override
  public void trackStopSession() {
    track(AndroidProfilerEvent.Type.SESSION_STOPPED);
  }

  @Override
  public void trackSessionsPanelStateChanged(boolean isExpanded) {
    track(isExpanded ? AndroidProfilerEvent.Type.SESSION_UI_EXPANDED : AndroidProfilerEvent.Type.SESSION_UI_COLLAPSED);
  }

  @Override
  public void trackSessionsPanelResized() {
    track(AndroidProfilerEvent.Type.SESSION_UI_RESIZED);
  }

  @Override
  public void trackSessionArtifactSelected(@NotNull SessionArtifact artifact, boolean isSessionLive) {
    ProfilerSessionSelectionMetaData.Builder builder = ProfilerSessionSelectionMetaData.newBuilder()
      .setSelectedType(
        SESSION_ARTIFACT_MAP.getOrDefault(artifact.getClass(), ProfilerSessionSelectionMetaData.ArtifactType.UNKNOWN_ARTIFACT_TYPE))
      .setIsSessionAlive(isSessionLive);
    newTracker(AndroidProfilerEvent.Type.SESSION_ARTIFACT_SELECTED).setSessionSelectionMetadata(builder.build()).track();
  }

  @Override
  public void trackGoBack() {
    track(AndroidProfilerEvent.Type.GO_BACK);
  }

  @Override
  public void trackSelectMonitor() {
    track(AndroidProfilerEvent.Type.SELECT_MONITOR);
  }

  @Override
  public void trackZoomIn() {
    track(AndroidProfilerEvent.Type.ZOOM_IN);
  }

  @Override
  public void trackZoomOut() {
    track(AndroidProfilerEvent.Type.ZOOM_OUT);
  }

  @Override
  public void trackResetZoom() {
    track(AndroidProfilerEvent.Type.ZOOM_RESET);
  }

  @Override
  public void trackZoomToSelection() {
    track(AndroidProfilerEvent.Type.ZOOM_TO_SELECTION);
  }

  @Override
  public void trackToggleStreaming() {
    track(AndroidProfilerEvent.Type.GO_LIVE);
  }

  @Override
  public void trackNavigateToCode() {
    track(AndroidProfilerEvent.Type.NAVIGATE_TO_CODE);
  }

  @Override
  public void trackToggleCpuThreadsHideablePanel() {
    track(AndroidProfilerEvent.Type.THREADS_VIEW_TOGGLED);
  }

  @Override
  public void trackSelectRange() {
    // We set the device when tracking range selection because we need to distinguish selections made on pre-O and post-O devices.
    newTracker(AndroidProfilerEvent.Type.SELECT_RANGE).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackCaptureTrace(@NotNull com.android.tools.profilers.cpu.CpuCaptureMetadata cpuCaptureMetadata) {
    newTracker(AndroidProfilerEvent.Type.CAPTURE_TRACE).setDevice(myActiveDevice).setCpuCaptureMetadata(cpuCaptureMetadata).track();
  }

  @Override
  public void trackImportTrace(@NotNull Cpu.CpuTraceType profilerType, boolean success) {
    CpuImportTraceMetadata.Builder metadata = CpuImportTraceMetadata.newBuilder();
    metadata.setImportStatus(success ? CpuImportTraceMetadata.ImportStatus.IMPORT_TRACE_SUCCESS
                                     : CpuImportTraceMetadata.ImportStatus.IMPORT_TRACE_FAILURE);
    switch (profilerType) {
      case ART:
        metadata.setTechnology(CpuImportTraceMetadata.Technology.ART_TECHNOLOGY);
        break;
      case SIMPLEPERF:
        metadata.setTechnology(CpuImportTraceMetadata.Technology.SIMPLEPERF_TECHNOLOGY);
        break;
      case ATRACE:
        metadata.setTechnology(CpuImportTraceMetadata.Technology.ATRACE_TECHNOLOGY);
        break;
      default:
        metadata.setTechnology(CpuImportTraceMetadata.Technology.UNKNOWN_TECHNOLOGY);
        break;
    }
    newTracker(AndroidProfilerEvent.Type.CPU_IMPORT_TRACE).setDevice(myActiveDevice).setCpuImportTraceMetadata(metadata.build()).track();
  }

  @Override
  public void trackCpuStartupProfiling(@NotNull Common.Device device, @NotNull ProfilingConfiguration configuration) {
    newTracker(AndroidProfilerEvent.Type.CPU_STARTUP_PROFILING).setDevice(device).setCpuStartupProfilingConfiguration(configuration)
      .track();
  }

  @Override
  public void trackCpuApiTracing(boolean sampling, boolean pathProvided, int bufferSize, int flags, int intervalUs) {
    CpuApiTracingMetadata metadata =
      CpuApiTracingMetadata.newBuilder().setUseSampling(sampling).setArgTracePath(pathProvided).setArgBufferSize(bufferSize)
        .setArgFlags(flags).setArgIntervalUs(intervalUs).build();
    newTracker(AndroidProfilerEvent.Type.CPU_API_TRACING).setDevice(myActiveDevice).setCpuApiTracingMetadata(metadata)
      .track();
  }

  @Override
  public void trackSelectThread() {
    track(AndroidProfilerEvent.Type.SELECT_THREAD);
  }

  @Override
  public void trackSelectCaptureTopDown() {
    track(AndroidProfilerEvent.Type.SELECT_TOP_DOWN);
  }

  @Override
  public void trackSelectCaptureBottomUp() {
    track(AndroidProfilerEvent.Type.SELECT_BOTTOM_UP);
  }

  @Override
  public void trackSelectCaptureFlameChart() {
    track(AndroidProfilerEvent.Type.SELECT_FLAME_CHART);
  }

  @Override
  public void trackForceGc() {
    track(AndroidProfilerEvent.Type.FORCE_GC);
  }

  @Override
  public void trackDumpHeap() {
    track(AndroidProfilerEvent.Type.SNAPSHOT_HPROF);
  }

  @Override
  public void trackRecordAllocations() {
    // Adding device information to capture allocations so we can tell if the device is Q+ for native allocation tracking.
    newTracker(AndroidProfilerEvent.Type.CAPTURE_ALLOCATIONS).setDevice(myActiveDevice).track();
  }

  @Override
  public void trackExportHeap() {
    track(AndroidProfilerEvent.Type.EXPORT_HPROF);
  }

  @Override
  public void trackExportAllocation() {
    track(AndroidProfilerEvent.Type.EXPORT_ALLOCATION);
  }

  @Override
  public void trackChangeClassArrangment() {
    track(AndroidProfilerEvent.Type.ARRANGE_CLASSES);
  }

  @Override
  public void trackSelectMemoryStack() {
    track(AndroidProfilerEvent.Type.SELECT_MEMORY_STACK);
  }

  @Override
  public void trackSelectMemoryReferences() {
    track(AndroidProfilerEvent.Type.SELECT_MEMORY_REFERENCES);
  }

  @Override
  public void trackSelectMemoryHeap(@NotNull String heapName) {
    AndroidProfilerEvent.MemoryHeap heapType;
    switch (heapName) {
      case CaptureObject.DEFAULT_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.DEFAULT_HEAP;
        break;
      case CaptureObject.APP_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.APP_HEAP;
        break;
      case CaptureObject.IMAGE_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.IMAGE_HEAP;
        break;
      case CaptureObject.ZYGOTE_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.ZYGOTE_HEAP;
        break;
      case CaptureObject.JNI_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.JNI_HEAP;
        break;
      case CaptureObject.NATIVE_HEAP_NAME:
        heapType = AndroidProfilerEvent.MemoryHeap.NATIVE_HEAP;
        break;
      default:
        getLogger().error("Attempt to report selection of unknown heap name: " + heapName);
        return;
    }
    newTracker(AndroidProfilerEvent.Type.SELECT_MEMORY_HEAP).setMemoryHeapId(heapType).track();
  }

  @Override
  public void trackSelectNetworkRequest() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTION);
  }

  @Override
  public void trackSelectNetworkDetailsOverview() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_OVERVIEW);
  }

  @Override
  public void trackSelectNetworkDetailsResponse() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_RESPONSE);
  }

  @Override
  public void trackSelectNetworkDetailsRequest() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_REQUEST);
  }

  @Override
  public void trackSelectNetworkDetailsStack() {
    track(AndroidProfilerEvent.Type.SELECT_DETAILS_STACK);
  }

  @Override
  public void trackSelectNetworkConnectionsView() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTIONS_CONNECTION_VIEW);
  }

  @Override
  public void trackSelectNetworkThreadsView() {
    track(AndroidProfilerEvent.Type.SELECT_CONNECTIONS_THREADS_VIEW);
  }

  @Override
  public void trackOpenProfilingConfigDialog() {
    track(AndroidProfilerEvent.Type.OPEN_CPU_CONFIG_DIALOG);
  }

  @Override
  public void trackCreateCustomProfilingConfig() {
    track(AndroidProfilerEvent.Type.CREATE_CPU_CONFIG);
  }

  @Override
  public void trackSelectEnergyRange(@NotNull com.android.tools.profilers.analytics.energy.EnergyRangeMetadata rangeMetadata) {
    newTracker(AndroidProfilerEvent.Type.SELECT_ENERGY_RANGE).setEnergyRangeMetadata(rangeMetadata).track();
  }

  @Override
  public void trackSelectEnergyEvent(@NotNull com.android.tools.profilers.analytics.energy.EnergyEventMetadata eventMetadata) {
    newTracker(AndroidProfilerEvent.Type.SELECT_ENERGY_EVENT).setEnergyEventMetadata(eventMetadata).track();
  }

  @Override
  public void trackFilterMetadata(@NotNull com.android.tools.profilers.analytics.FilterMetadata filterMetadata) {
    newTracker(AndroidProfilerEvent.Type.FILTER).setFilterMetadata(filterMetadata).track();
  }

  @Override
  public void trackMemoryProfilerInstanceFilter(@NotNull CaptureObjectInstanceFilter filter) {
    MemoryInstanceFilterMetadata.Builder builder = MemoryInstanceFilterMetadata.newBuilder()
      .setFilterType(
        MEMORY_INSTANCE_FILTER_MAP.getOrDefault(filter.getClass(), MemoryInstanceFilterMetadata.FilterType.UNKNOWN_FILTER_TYPE));
    newTracker(AndroidProfilerEvent.Type.MEMORY_INSTANCE_FILTER).setMemoryInstanceFilterMetadata(builder.build()).track();
  }

  @Override
  public void trackTraceProcessorDaemonSpawnAttempt(boolean successful, long timeToSpawnMs) {
    TraceProcessorDaemonManagerStats stats = TraceProcessorDaemonManagerStats.newBuilder()
      .setTimeToSpawnMs(timeToSpawnMs)
      .build();
    AndroidProfilerEvent.Type type = successful ? AndroidProfilerEvent.Type.TPD_MANAGER_SPAWN_OK
                                                : AndroidProfilerEvent.Type.TPD_MANAGER_SPAWN_FAILED;
    newTracker(type).setTraceProcessorDaemonManagerStats(stats).track();
  }

  @Override
  public void trackTraceProcessorLoadTrace(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs, long traceSizeBytes) {
    TraceProcessorDaemonQueryStats stats = TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(queryStatus)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)
      .setTraceSizeBytes(traceSizeBytes)
      .build();

    newTracker(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_TRACE).setTraceProcessorDaemonQueryStats(stats).track();
  }

  @Override
  public void trackTraceProcessorProcessMetadata(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs) {
    TraceProcessorDaemonQueryStats stats = TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(queryStatus)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)
      .build();

    newTracker(AndroidProfilerEvent.Type.TPD_QUERY_PROCESS_METADATA).setTraceProcessorDaemonQueryStats(stats).track();
  }

  @Override
  public void trackTraceProcessorCpuData(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs) {
    TraceProcessorDaemonQueryStats stats = TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(queryStatus)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)
      .build();

    newTracker(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_CPU_DATA).setTraceProcessorDaemonQueryStats(stats).track();
  }

  @Override
  public void trackTraceProcessorMemoryData(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs) {
    TraceProcessorDaemonQueryStats stats = TraceProcessorDaemonQueryStats.newBuilder()
      .setQueryStatus(queryStatus)
      .setMethodDurationMs(methodTimeMs)
      .setGrpcQueryDurationMs(queryTimeMs)
      .build();

    newTracker(AndroidProfilerEvent.Type.TPD_QUERY_LOAD_MEMORY_DATA).setTraceProcessorDaemonQueryStats(stats).track();
  }

  @Override
  public void trackMoveTrackGroupUp(@NotNull String title) {
    trackTrackGroupAction(title, AdtUiTrackGroupMetadata.TrackGroupActionType.MOVE_UP);
  }

  @Override
  public void trackMoveTrackGroupDown(@NotNull String title) {
    trackTrackGroupAction(title, AdtUiTrackGroupMetadata.TrackGroupActionType.MOVE_DOWN);
  }

  @Override
  public void trackExpandTrackGroup(@NotNull String title) {
    trackTrackGroupAction(title, AdtUiTrackGroupMetadata.TrackGroupActionType.EXPAND);
  }

  @Override
  public void trackCollapseTrackGroup(@NotNull String title) {
    trackTrackGroupAction(title, AdtUiTrackGroupMetadata.TrackGroupActionType.COLLAPSE);
  }

  private void trackTrackGroupAction(@NotNull String title, @NotNull AdtUiTrackGroupMetadata.TrackGroupActionType actionType) {
    newTracker(AndroidProfilerEvent.Type.TRACK_GROUP_ACTION).setTrackGroupMetadata(
      AdtUiTrackGroupMetadata.newBuilder()
        .setTitle(title)
        .setActionType(actionType)
        .build()
    ).track();
  }

  @Override
  public void trackSelectBox(long durationUs, int trackCount) {
    newTracker(AndroidProfilerEvent.Type.SELECT_BOX).setBoxSelectionMetadata(
      AdtUiBoxSelectionMetadata.newBuilder()
        .setDurationUs(durationUs)
        .setTrackCount(trackCount)
        .build()
    ).track();
  }

  @Override
  public void trackFrameSelectionPerTrace(int count) {
    newTracker(AndroidProfilerEvent.Type.SELECT_FRAME).setEventCount(count).track();
  }

  @Override
  public void trackAllFrameTogglingPerTrace(int count) {
    newTracker(AndroidProfilerEvent.Type.TOGGLE_ALL_FRAMES).setEventCount(count).track();
  }

  @Override
  public void trackLifecycleTogglingPerTrace(int count) {
    newTracker(AndroidProfilerEvent.Type.TOGGLE_LIFECYCLE).setEventCount(count).track();
  }

  @Override
  public void trackNetworkMigrationDialogSelected() {
    UsageTracker.log(
      UsageTrackerUtils.withProjectId(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.APP_INSPECTION)
          .setAppInspectionEvent(
            AppInspectionEvent.newBuilder()
              .setType(AppInspectionEvent.Type.INSPECTOR_EVENT)
              .setNetworkInspectorEvent(
                AppInspectionEvent.NetworkInspectorEvent.newBuilder()
                  .setType(AppInspectionEvent.NetworkInspectorEvent.Type.MIGRATION_LINK_SELECTED)
              )
          ),
        myTrackingProject
      )
    );
  }

  @Override
  public void trackLoading(AndroidProfilerEvent.Loading loading) {
    newTracker(AndroidProfilerEvent.Type.LOADING).setLoading(loading).track();
  }

  /**
   * Convenience method for creating a new tracker with all the minimum data supplied.
   */
  @NotNull
  private Tracker newTracker(AndroidProfilerEvent.Type eventType) {
    return new Tracker(myTrackingProject, eventType, myCurrStage);
  }

  /**
   * Convenience method for the most common tracking scenario (just an event with no extra data).
   * If other data should be sent with this message, explicitly create a {@link Tracker} and use
   * {@link Tracker#track()} instead.
   */
  private void track(AndroidProfilerEvent.Type eventType) {
    newTracker(eventType).track();
  }

  private static final class Tracker {
    @NotNull private final AndroidProfilerEvent.Type myEventType;
    @NotNull private final AndroidProfilerEvent.Stage myCurrStage;
    @NotNull private final Project myTrackingProject;
    @Nullable private Common.Device myDevice;
    @Nullable private com.android.tools.profilers.cpu.CpuCaptureMetadata myCpuCaptureMetadata;
    @Nullable private CpuImportTraceMetadata myCpuImportTraceMetadata;
    @Nullable private com.android.tools.profilers.analytics.FilterMetadata myFeatureMetadata;
    @Nullable private CpuApiTracingMetadata myCpuApiTracingMetadata;
    @Nullable private com.android.tools.profilers.analytics.energy.EnergyRangeMetadata myEnergyRangeMetadata;
    @Nullable private com.android.tools.profilers.analytics.energy.EnergyEventMetadata myEnergyEventMetadata;
    @Nullable private ProfilerSessionCreationMetaData mySessionCreationMetadata;
    @Nullable private ProfilerSessionSelectionMetaData mySessionArtifactMetadata;
    @Nullable private ProfilingConfiguration myCpuStartupProfilingConfiguration;
    @Nullable private TransportFailureMetadata myTransportFailureMetadata;
    @Nullable private MemoryInstanceFilterMetadata myMemoryInstanceFilterMetadata;
    @Nullable private TraceProcessorDaemonManagerStats myTraceProcessorDaemonManagerStats;
    @Nullable private TraceProcessorDaemonQueryStats myTraceProcessorDaemonQueryStats;
    @Nullable private AdtUiTrackGroupMetadata myTrackGroupMetadata;
    @Nullable private AdtUiBoxSelectionMetadata myBoxSelectionMetadata;
    private int myEventCount = 0;
    @Nullable private AndroidProfilerEvent.Loading myLoading;

    @Nullable private RunWithProfilingMetadata myRunWithProfilingMetadata;

    private AndroidProfilerEvent.MemoryHeap myMemoryHeap = AndroidProfilerEvent.MemoryHeap.UNKNOWN_HEAP;

    public Tracker(@NotNull Project trackingProject,
                   @NotNull AndroidProfilerEvent.Type eventType,
                   @NotNull AndroidProfilerEvent.Stage stage) {
      myEventType = eventType;
      myCurrStage = stage;
      myTrackingProject = trackingProject;
    }

    @NotNull
    public Tracker setDevice(@Nullable Common.Device device) {
      myDevice = device;
      return this;
    }

    @NotNull
    public Tracker setCpuCaptureMetadata(@Nullable com.android.tools.profilers.cpu.CpuCaptureMetadata cpuCaptureMetadata) {
      myCpuCaptureMetadata = cpuCaptureMetadata;
      return this;
    }

    @NotNull
    public Tracker setCpuImportTraceMetadata(CpuImportTraceMetadata cpuImportTraceMetadata) {
      myCpuImportTraceMetadata = cpuImportTraceMetadata;
      return this;
    }

    @NotNull
    public Tracker setCpuStartupProfilingConfiguration(@Nullable ProfilingConfiguration configuration) {
      myCpuStartupProfilingConfiguration = configuration;
      return this;
    }

    @NotNull
    public Tracker setCpuApiTracingMetadata(@Nullable CpuApiTracingMetadata metadata) {
      myCpuApiTracingMetadata = metadata;
      return this;
    }

    @NotNull
    public Tracker setFilterMetadata(@Nullable com.android.tools.profilers.analytics.FilterMetadata filterMetadata) {
      myFeatureMetadata = filterMetadata;
      return this;
    }

    @NotNull
    public Tracker setEnergyRangeMetadata(@Nullable com.android.tools.profilers.analytics.energy.EnergyRangeMetadata energyRangeMetadata) {
      myEnergyRangeMetadata = energyRangeMetadata;
      return this;
    }

    @NotNull
    public Tracker setEnergyEventMetadata(@Nullable com.android.tools.profilers.analytics.energy.EnergyEventMetadata energyEventMetadata) {
      myEnergyEventMetadata = energyEventMetadata;
      return this;
    }

    @NotNull
    public Tracker setMemoryHeapId(AndroidProfilerEvent.MemoryHeap heap) {
      myMemoryHeap = heap;
      return this;
    }

    @NotNull
    public Tracker setSessionCreationMetadata(ProfilerSessionCreationMetaData metadata) {
      mySessionCreationMetadata = metadata;
      return this;
    }

    @NotNull
    public Tracker setSessionSelectionMetadata(ProfilerSessionSelectionMetaData metadata) {
      mySessionArtifactMetadata = metadata;
      return this;
    }

    @NotNull
    public Tracker setTransportFailureMetadata(TransportFailureMetadata metadata) {
      myTransportFailureMetadata = metadata;
      return this;
    }

    @NotNull
    public Tracker setMemoryInstanceFilterMetadata(MemoryInstanceFilterMetadata metadata) {
      myMemoryInstanceFilterMetadata = metadata;
      return this;
    }

    @NotNull
    public Tracker setTraceProcessorDaemonManagerStats(TraceProcessorDaemonManagerStats traceProcessorDaemonManagerStats) {
      myTraceProcessorDaemonManagerStats = traceProcessorDaemonManagerStats;
      return this;
    }

    @NotNull
    public Tracker setTraceProcessorDaemonQueryStats(TraceProcessorDaemonQueryStats traceProcessorDaemonQueryStats) {
      myTraceProcessorDaemonQueryStats = traceProcessorDaemonQueryStats;
      return this;
    }

    @NotNull
    public Tracker setTrackGroupMetadata(AdtUiTrackGroupMetadata trackGroupMetadata) {
      myTrackGroupMetadata = trackGroupMetadata;
      return this;
    }

    @NotNull
    private Tracker setBoxSelectionMetadata(AdtUiBoxSelectionMetadata boxSelectionMetadata) {
      myBoxSelectionMetadata = boxSelectionMetadata;
      return this;
    }

    @NotNull
    private Tracker setEventCount(int eventCount) {
      myEventCount = eventCount;
      return this;
    }

    @NotNull
    private Tracker setLoading(AndroidProfilerEvent.Loading loading) {
      myLoading = loading;
      return this;
    }

    @NotNull
    private Tracker setRunWithProfilingMetadata(@NotNull RunWithProfilingMetadata metadata) {
      myRunWithProfilingMetadata = metadata;
      return this;
    }

    public void track() {
      AndroidProfilerEvent.Builder profilerEvent = AndroidProfilerEvent.newBuilder().setStage(myCurrStage).setType(myEventType);

      populateCpuCaptureMetadata(profilerEvent);
      populateFilterMetadata(profilerEvent);
      populateEnergyRangeMetadata(profilerEvent);
      populateEnergyEventMetadata(profilerEvent);
      populateMemoryInstanceFilterMetadata(profilerEvent);

      switch (myEventType) {
        case SELECT_MEMORY_HEAP:
          profilerEvent.setMemoryHeap(myMemoryHeap);
          break;
        case SESSION_CREATED:
          profilerEvent.setSessionStartMetadata(mySessionCreationMetadata);
          break;
        case SESSION_ARTIFACT_SELECTED:
          profilerEvent.setSessionArtifactMetadata(mySessionArtifactMetadata);
          break;
        case TRANSPORT_DAEMON_FAILED:
        case TRANSPORT_PROXY_FAILED:
          assert myTransportFailureMetadata != null;
          profilerEvent.setTransportFailureMetadata(myTransportFailureMetadata);
          break;
        case CPU_API_TRACING:
          profilerEvent.setCpuApiTracingMetadata(myCpuApiTracingMetadata);
          break;
        case CPU_STARTUP_PROFILING:
          profilerEvent.setCpuStartupProfilingMetadata(CpuStartupProfilingMetadata
                                                         .newBuilder()
                                                         .setProfilingConfig(
                                                           toStatsCpuProfilingConfig(myCpuStartupProfilingConfiguration)));
          break;
        case CPU_IMPORT_TRACE:
          assert myCpuImportTraceMetadata != null;
          profilerEvent.setCpuImportTraceMetadata(myCpuImportTraceMetadata);
          break;
        case TPD_MANAGER_SPAWN_OK: // Fallthrough
        case TPD_MANAGER_SPAWN_FAILED:
          profilerEvent.setTpdManagerStats(myTraceProcessorDaemonManagerStats);
          break;
        case TPD_QUERY_LOAD_TRACE: // Fallthrough
        case TPD_QUERY_PROCESS_METADATA: // Fallthrough
        case TPD_QUERY_LOAD_CPU_DATA: // Fallthrough
        case TPD_QUERY_LOAD_MEMORY_DATA:
          profilerEvent.setTpdQueryStats(myTraceProcessorDaemonQueryStats);
          break;
        case TRACK_GROUP_ACTION:
          profilerEvent.setTrackGroupMetadata(myTrackGroupMetadata);
          break;
        case SELECT_BOX:
          profilerEvent.setBoxSelectionMetadata(myBoxSelectionMetadata);
          break;
        case SELECT_FRAME: // Fallthrough
        case TOGGLE_ALL_FRAMES: // Fallthrough
        case TOGGLE_LIFECYCLE:
          profilerEvent.setEventCount(myEventCount);
          break;
        case LOADING:
          profilerEvent.setLoading(myLoading);
          break;
        case RUN_WITH_PROFILING:
          profilerEvent.setRunWithProfilingMetadata(myRunWithProfilingMetadata);
          break;
        default:
          break;
      }

      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ANDROID_PROFILER)
        .setAndroidProfilerEvent(profilerEvent);

      if (myDevice != null) {
        event.setDeviceInfo(
          // Set the properties consistently with AndroidStudioUsageTracker.deviceToDeviceInfo().
          DeviceInfo.newBuilder()
            .setAnonymizedSerialNumber(AnonymizerUtil.anonymizeUtf8(myDevice.getSerial()))
            .setBuildTags(myDevice.getBuildTags())
            .setBuildType(myDevice.getBuildType())
            .setBuildVersionRelease(myDevice.getVersion())
            .setBuildApiLevelFull(new AndroidVersion(myDevice.getApiLevel(), myDevice.getCodename()).getApiString())
            .setCpuAbi(CommonMetricsData.applicationBinaryInterfaceFromString(myDevice.getCpuAbi()))
            .setManufacturer(myDevice.getManufacturer())
            .setDeviceType(myDevice.getIsEmulator() ? DeviceInfo.DeviceType.LOCAL_EMULATOR : DeviceInfo.DeviceType.LOCAL_PHYSICAL)
            .setMdnsConnectionType(isMdnsAutoConnectUnencrypted(myDevice.getSerial()) ?
                                   DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_UNENCRYPTED :
                                   isMdnsAutoConnectTls(myDevice.getSerial()) ?
                                   DeviceInfo.MdnsConnectionType.MDNS_AUTO_CONNECT_TLS :
                                   DeviceInfo.MdnsConnectionType.MDNS_NONE)
            .setModel(myDevice.getModel())
            .build());
      }

      UsageTracker.log(UsageTrackerUtils.withProjectId(event, myTrackingProject));
    }

    private void populateEnergyRangeMetadata(@NotNull AndroidProfilerEvent.Builder profilerEvent) {
      if (myEnergyRangeMetadata == null) {
        return;
      }

      EnergyRangeMetadata.Builder builder = EnergyRangeMetadata.newBuilder();
      myEnergyRangeMetadata.getEventCounts().forEach(eventCount -> {
        builder.addEventCounts(EnergyEventCount.newBuilder()
                                 .setType(toEnergyType(eventCount.getKind()))
                                 .setCount(eventCount.getCount())
                                 .build());
      });

      profilerEvent.setEnergyRangeMetadata(builder.build());
    }

    private void populateEnergyEventMetadata(@NotNull AndroidProfilerEvent.Builder profilerEvent) {
      if (myEnergyEventMetadata == null || myEnergyEventMetadata.getSubevents().isEmpty()) {
        return;
      }

      EnergyEventMetadata.Builder builder = EnergyEventMetadata.newBuilder();

      List<Common.Event> subevents = myEnergyEventMetadata.getSubevents();
      Common.Event firstEvent = subevents.get(0);
      builder.setType(toEnergyType(firstEvent.getEnergyEvent()));

      EnergyEvent.Subtype eventSubtype = toEnergySubtype(firstEvent.getEnergyEvent());
      if (eventSubtype != null) {
        builder.setSubtype(eventSubtype);
      }

      for (Common.Event event : subevents) {
        builder.addSubevents(toEnergySubevent(event.getEnergyEvent()));
      }

      profilerEvent.setEnergyEventMetadata(builder);
    }

    private void populateMemoryInstanceFilterMetadata(@NotNull AndroidProfilerEvent.Builder profilerEvent) {
      if (myMemoryInstanceFilterMetadata == null) {
        return;
      }

      profilerEvent.setMemoryInstanceFilterMetadata(myMemoryInstanceFilterMetadata);
    }

    private void populateFilterMetadata(AndroidProfilerEvent.Builder profilerEvent) {
      if (myFeatureMetadata != null) {
        FilterMetadata.Builder filterMetadata = FilterMetadata.newBuilder();
        filterMetadata.setFeaturesUsed(myFeatureMetadata.getFeaturesUsed());
        filterMetadata.setMatchedElements(myFeatureMetadata.getMatchedElementCount());
        filterMetadata.setTotalElements(myFeatureMetadata.getTotalElementCount());
        filterMetadata.setSearchLength(myFeatureMetadata.getFilterTextLength());
        switch (myFeatureMetadata.getView()) {
          case UNKNOWN_FILTER_VIEW:
            filterMetadata.setActiveView(FilterMetadata.View.UNKNOWN_FILTER_VIEW);
            break;
          case CPU_TOP_DOWN:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_TOP_DOWN);
            break;
          case CPU_BOTTOM_UP:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_BOTTOM_UP);
            break;
          case CPU_FLAME_CHART:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_FLAME_CHART);
            break;
          case CPU_CALL_CHART:
            filterMetadata.setActiveView(FilterMetadata.View.CPU_CALL_CHART);
            break;
          case MEMORY_CALLSTACK:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_CALLSTACK);
            break;
          case MEMORY_PACKAGE:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_PACKAGE);
            break;
          case MEMORY_CLASS:
            filterMetadata.setActiveView(FilterMetadata.View.MEMORY_CLASS);
            break;
          case NETWORK_CONNECTIONS:
            filterMetadata.setActiveView(FilterMetadata.View.NETWORK_CONNECTIONS);
            break;
          case NETWORK_THREADS:
            filterMetadata.setActiveView(FilterMetadata.View.NETWORK_THREADS);
            break;
        }
        profilerEvent.setFilterMetadata(filterMetadata);
      }
    }

    private void populateCpuCaptureMetadata(AndroidProfilerEvent.Builder profilerEvent) {
      if (myCpuCaptureMetadata != null) {
        CpuCaptureMetadata.Builder captureMetadata = CpuCaptureMetadata.newBuilder()
          .setCaptureDurationMs(myCpuCaptureMetadata.getCaptureDurationMs())
          .setRecordDurationMs(myCpuCaptureMetadata.getRecordDurationMs())
          .setTraceFileSizeBytes(myCpuCaptureMetadata.getTraceFileSizeBytes())
          .setParsingTimeMs(myCpuCaptureMetadata.getParsingTimeMs())
          .setStoppingTimeMs(myCpuCaptureMetadata.getStoppingTimeMs())
          .setCaptureStatus(
            CPU_CAPTURE_STATUS_MAP.getOrDefault(myCpuCaptureMetadata.getStatus(), CpuCaptureMetadata.CaptureStatus.SUCCESS));

        captureMetadata.setProfilingConfig(toStatsCpuProfilingConfig(myCpuCaptureMetadata.getProfilingConfiguration()));
        if (myCpuCaptureMetadata.getProfilingConfiguration().getTraceType() == Cpu.CpuTraceType.ART) {
          captureMetadata.setArtStopTimeoutSec(CpuProfilerStage.CPU_ART_STOP_TIMEOUT_SEC);
        }
        profilerEvent.setCpuCaptureMetadata(captureMetadata);
      }
    }

    /**
     * Converts the given {@link ProfilingConfiguration} to the representation in analytics, i.e to {@link CpuProfilingConfig}.
     */
    @NotNull
    private static CpuProfilingConfig toStatsCpuProfilingConfig(@NotNull ProfilingConfiguration config) {
      CpuProfilingConfig.Builder cpuConfigInfo = CpuProfilingConfig.newBuilder();
      Cpu.CpuTraceConfiguration.UserOptions options = config.toProto();
      switch (config.getTraceType()) {
        case ART:
          cpuConfigInfo.setType(CpuProfilingConfig.Type.ART);
          cpuConfigInfo.setMode(config instanceof ArtSampledConfiguration
                                ? CpuProfilingConfig.Mode.SAMPLED
                                : CpuProfilingConfig.Mode.INSTRUMENTED);
          cpuConfigInfo.setSampleInterval(options.getSamplingIntervalUs());
          break;
        case SIMPLEPERF:
          cpuConfigInfo.setType(CpuProfilingConfig.Type.SIMPLE_PERF);
          cpuConfigInfo.setMode(CpuProfilingConfig.Mode.SAMPLED);
          cpuConfigInfo.setSampleInterval(options.getSamplingIntervalUs());
          break;
        case ATRACE:
          cpuConfigInfo.setType(CpuProfilingConfig.Type.ATRACE);
          cpuConfigInfo.setSizeLimit(options.getBufferSizeInMb());
          break;
        case PERFETTO:
          cpuConfigInfo.setType(CpuProfilingConfig.Type.PERFETTO);
          cpuConfigInfo.setSizeLimit(options.getBufferSizeInMb());
          break;
        case UNSPECIFIED_TYPE:
        case UNRECOGNIZED:
          break;
      }
      return cpuConfigInfo.build();
    }

    @NotNull
    private static EnergyEvent.Type toEnergyType(@NotNull Energy.EnergyEventData energyEvent) {
      return toEnergyType(EnergyDuration.Kind.from(energyEvent));
    }

    @NotNull
    private static EnergyEvent.Type toEnergyType(@NotNull EnergyDuration.Kind energyKind) {
      switch (energyKind) {
        case WAKE_LOCK:
          return EnergyEvent.Type.WAKE_LOCK;
        case ALARM:
          return EnergyEvent.Type.ALARM;
        case JOB:
          return EnergyEvent.Type.JOB;
        case LOCATION:
          return EnergyEvent.Type.LOCATION;
        default:
          return EnergyEvent.Type.UNKNOWN_EVENT_TYPE;
      }
    }

    /**
     * Returns the subtype of the current event, if it has one, or {@code null} if none.
     *
     * @param eventData
     */
    @Nullable
    private static EnergyEvent.Subtype toEnergySubtype(@NotNull Energy.EnergyEventData eventData) {
      if (eventData.getMetadataCase() == Energy.EnergyEventData.MetadataCase.WAKE_LOCK_ACQUIRED) {
        Energy.WakeLockAcquired wakeLockAcquired = eventData.getWakeLockAcquired();
        switch (wakeLockAcquired.getLevel()) {
          case PARTIAL_WAKE_LOCK:
            return EnergyEvent.Subtype.WAKE_LOCK_PARTIAL;
          case SCREEN_DIM_WAKE_LOCK:
            return EnergyEvent.Subtype.WAKE_LOCK_SCREEN_DIM;
          case SCREEN_BRIGHT_WAKE_LOCK:
            return EnergyEvent.Subtype.WAKE_LOCK_SCREEN_BRIGHT;
          case FULL_WAKE_LOCK:
            return EnergyEvent.Subtype.WAKE_LOCK_FULL;
          case PROXIMITY_SCREEN_OFF_WAKE_LOCK:
            return EnergyEvent.Subtype.WAKE_LOCK_PROXIMITY_SCREEN_OFF;
          // Default case should never happen unless framework adds a new wake lock type and we forget to handle it
          default:
            return EnergyEvent.Subtype.UNKNOWN_EVENT_SUBTYPE;
        }
      }
      else if (eventData.getMetadataCase() == Energy.EnergyEventData.MetadataCase.ALARM_SET) {
        Energy.AlarmSet alarmSet = eventData.getAlarmSet();
        switch (alarmSet.getType()) {
          case RTC:
            return EnergyEvent.Subtype.ALARM_RTC;
          case RTC_WAKEUP:
            return EnergyEvent.Subtype.ALARM_RTC_WAKEUP;
          case ELAPSED_REALTIME:
            return EnergyEvent.Subtype.ALARM_ELAPSED_REALTIME;
          case ELAPSED_REALTIME_WAKEUP:
            return EnergyEvent.Subtype.ALARM_ELAPSED_REALTIME_WAKEUP;
          // Default case should never happen unless framework adds a new alarm type and we forget to handle it
          default:
            return EnergyEvent.Subtype.UNKNOWN_EVENT_SUBTYPE;
        }
      }

      return null;
    }

    @NotNull
    private static EnergyEvent.Subevent toEnergySubevent(@NotNull Energy.EnergyEventData eventData) {
      switch (eventData.getMetadataCase()) {
        case WAKE_LOCK_ACQUIRED:
          return EnergyEvent.Subevent.WAKE_LOCK_ACQUIRED;
        case WAKE_LOCK_RELEASED:
          return EnergyEvent.Subevent.WAKE_LOCK_RELEASED;
        case ALARM_SET:
          return EnergyEvent.Subevent.ALARM_SET;
        case ALARM_CANCELLED:
          return EnergyEvent.Subevent.ALARM_CANCELLED;
        case ALARM_FIRED:
          return EnergyEvent.Subevent.ALARM_FIRED;
        case JOB_SCHEDULED:
          return EnergyEvent.Subevent.JOB_SCHEDULED;
        case JOB_STARTED:
          return EnergyEvent.Subevent.JOB_STARTED;
        case JOB_STOPPED:
          return EnergyEvent.Subevent.JOB_STOPPED;
        case JOB_FINISHED:
          return EnergyEvent.Subevent.JOB_FINISHED;
        case LOCATION_UPDATE_REQUESTED:
          return EnergyEvent.Subevent.LOCATION_UPDATE_REQUESTED;
        case LOCATION_UPDATE_REMOVED:
          return EnergyEvent.Subevent.LOCATION_UPDATE_REMOVED;
        case LOCATION_CHANGED:
          return EnergyEvent.Subevent.LOCATION_CHANGED;
        default:
          return EnergyEvent.Subevent.UNKNOWN_ENERGY_SUBEVENT;
      }
    }
  }

  private static Logger getLogger() {
    return Logger.getInstance(StudioFeatureTracker.class);
  }
}
