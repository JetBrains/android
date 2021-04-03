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
package com.android.tools.profilers.analytics;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.analytics.energy.EnergyEventMetadata;
import com.android.tools.profilers.analytics.energy.EnergyRangeMetadata;
import com.android.tools.profilers.cpu.CpuCaptureMetadata;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service for tracking events that occur in our profilers, in order to understand and evaluate
 * how our users are using them.
 * <p>
 * The class that implements this should be sure to let users opt out of sending this information,
 * at which point all these methods should become no-ops.
 */
public interface FeatureTracker {

  /**
   * Track when the TransportDeviceManager detects an ddmlib device and is about to begin the logic
   * to launch the transport daemon.
   */
  void trackPreTransportDaemonStarts(@NotNull Common.Device transportDevice);

  /**
   * Track when the transport daemon fails to launch.
   */
  void trackTransportDaemonFailed(@NotNull Common.Device transportDevice, Exception exception);

  /**
   * Track when the transport proxy fails to connect the datastore and the daemon server.
   */
  void trackTransportProxyCreationFailed(@NotNull Common.Device transportDevice, Exception exception);

  /**
   * Track when the profilers failed to initiailize. This happens when the ProfilerService is
   * unavailable. (e.g. more than one Studio instance access profilers)
   */
  void trackProfilerInitializationFailed();

  /**
   * Track when we enter a new stage. The stage should always be included as state with all other
   * tracking events.
   */
  void trackEnterStage(AndroidProfilerEvent.Stage stageType);

  /**
   * Track when the user clicks the Profile button. This can happen via the tool bar, or the run menu.
   * This is in contrast to when the user attaches profilers to an app that was already running.
   */
  void trackRunWithProfiling();

  /**
   * Track when auto profiling is requested. e.g. when the user clicks "Profile", or "Run"/"Debug"
   * while the profilers window is opened.
   */
  void trackAutoProfilingRequested();

  /**
   * Track when auto profiling has found the matching process.
   */
  void trackAutoProfilingSucceeded();

  /**
   * Track when we begin profiling a target process.
   */
  void trackProfilingStarted();

  /**
   * Track when we learn that the process we are profiling is instrumented and allows us to query
   * advanced profiling information. This value will always be a subset of
   * {@link #trackProfilingStarted()}.
   */
  void trackAdvancedProfilingStarted();

  /**
   * Track when the user takes an action to change the current device. This will only be tracked
   * if the device actually changes.
   */
  void trackChangeDevice(@Nullable Common.Device device);

  /**
   * Track when the user takes an action to change the current process. This will only be tracked
   * if the process actually changes.
   */
  void trackChangeProcess(@Nullable Common.Process process);

  /**
   * Track when user presses the "+" button in the Sessions panel.
   */
  void trackSessionDropdownClicked();

  /**
   * Track when the user explicitly creates a new session via the Sessions UI.
   */
  void trackCreateSession(Common.SessionMetaData.SessionType sessionType, SessionsManager.SessionCreationSource sourceType);

  /**
   * Track when the user explicitly stops an ongoing profiling session, without starting a new one (e.g. selecting a new process).
   */
  void trackStopSession();

  /**
   * Track when the user toggles the Sessions panel.
   */
  void trackSessionsPanelStateChanged(boolean isExpanded);

  /**
   * Track when the user resizes the Sessions panel. This is only applicable in the expanded view.
   */
  void trackSessionsPanelResized();

  /**
   * Track when the user selects a session item in the UI, and whether the associated session is currently active.
   */
  void trackSessionArtifactSelected(@NotNull SessionArtifact artifact, boolean isSessionLive);

  /**
   * Track when the user takes an action to return back to the top-level monitor view (from a
   * specific profiler).
   */
  void trackGoBack();

  /**
   * Track when the user takes an action to change to a new monitor.
   */
  void trackSelectMonitor();

  /**
   * Track when the user takes an action to zoom in one level.
   */
  void trackZoomIn();

  /**
   * Track when the user takes an action to zoom out one level.
   */
  void trackZoomOut();

  /**
   * Track when the user takes an action to restore zoom to its default level.
   */
  void trackResetZoom();

  /**
   * Track when the user takes an action to zoom to the current selection.
   */
  void trackZoomToSelection();

  /**
   * Track the user toggling whether the profiler should stream or not.
   */
  void trackToggleStreaming();

  /**
   * Track the user navigating away from the profiler to some target source code.
   */
  void trackNavigateToCode();

  /**
   * Track anytime the user creates a range selection in any of our charts.
   */
  void trackSelectRange();

  /**
   * Track the user capturing a method trace.
   */
  void trackCaptureTrace(@NotNull CpuCaptureMetadata cpuCaptureMetadata);

  /**
   * Track the user importing a method trace.
   */
  void trackImportTrace(@NotNull Cpu.CpuTraceType traceType, boolean success);

  /**
   * Track the startup CPU profiling that was started with the given {@param device} and {@param configuration}.
   */
  void trackCpuStartupProfiling(@NotNull Common.Device device, @NotNull ProfilingConfiguration configuration);

  /**
   * @param sampling     True if using sampling; false if using instrumentation.
   * @param pathProvided A trace path is given and not null (we don't log the path as it might contain PII).
   * @param bufferSize   Buffer size as a given API argument (-1 if unavailable).
   * @param flags        Flags as a given API argument (-1 if unavailable).
   * @param intervalUs   Sampling interval as a given API argument (-1 if unavailable).
   */
  void trackCpuApiTracing(boolean sampling, boolean pathProvided, int bufferSize, int flags, int intervalUs);

  /**
   * Track the user clicking on one of the threads in the thread list.
   */
  void trackSelectThread();

  /**
   * Track the user opening up the "Top Down" tab in the CPU capture view
   */
  void trackSelectCaptureTopDown();

  /**
   * Track the user opening up the "Bottom Up" tab in the CPU capture view
   */
  void trackSelectCaptureBottomUp();

  /**
   * Track the user opening up the "Flame Chart" tab in the CPU capture view
   */
  void trackSelectCaptureFlameChart();

  /**
   * Track the user opening up the "Call Chart" tab in the CPU capture view
   */
  void trackSelectCaptureCallChart();

  /**
   * Track when the user requests memory be garbage collected.
   */
  void trackForceGc();

  /**
   * Track when the user takes a snapshot of the memory heap.
   */
  void trackDumpHeap();

  /**
   * Track when user finishes recording memory allocations
   */
  void trackRecordAllocations();

  /**
   * Track when the user exports a heap snapshot.
   * TODO: This needs to be hooked up.
   */
  void trackExportHeap();

  /**
   * Track when the user exports an allocation recording.
   * TODO: This needs to be hooked up.
   */
  void trackExportAllocation();

  /**
   * Track when the user changes the class-arrangement strategy.
   * TODO: This needs to be hooked up.
   */
  void trackChangeClassArrangment();

  /**
   * Track the user opening up the "Stack" tab in the memory details view.
   */
  void trackSelectMemoryStack();

  /**
   * Track the user opening up the "Reference" tab in the memory details view.
   */
  void trackSelectMemoryReferences();

  /**
   * Track the user selecting a heap in the memory heap combobox.
   */
  void trackSelectMemoryHeap(@NotNull String heapName);

  /**
   * Track the user selecting a row from a table of connections.
   */
  void trackSelectNetworkRequest();

  /**
   * Track the user opening up the "Overview" tab in the network details view.
   */
  void trackSelectNetworkDetailsOverview();

  /**
   * Track the user opening up the "Headers" tab in the network details view.
   */
  void trackSelectNetworkDetailsHeaders();

  /**
   * Track the user opening up the "Response" tab in the network details view.
   */
  void trackSelectNetworkDetailsResponse();

  /**
   * Track the user opening up the "Request" tab in the network details view.
   */
  void trackSelectNetworkDetailsRequest();

  /**
   * Track the user opening up the "Trace" tab in the network details view.
   */
  void trackSelectNetworkDetailsStack();

  /**
   * Track the user opening up the "Error" tab in the network details view.
   */
  void trackSelectNetworkDetailsError();

  /**
   * Track the user selecting the "Connections View" tab.
   */
  void trackSelectNetworkConnectionsView();

  /**
   * Track the user selecting the "Threads View" tab.
   */
  void trackSelectNetworkThreadsView();

  /**
   * Track the user opening up the CPU profiling configurations dialog.
   */
  void trackOpenProfilingConfigDialog();

  /**
   * Track the user creating custom CPU profiling configurations.
   */
  void trackCreateCustomProfilingConfig();

  /**
   * Track when the user uses the filter component in the profilers.
   */
  void trackFilterMetadata(@NotNull FilterMetadata filterMetadata);

  /**
   * Track when the user selects a thread via the cpu kernel list.
   */
  void trackSelectCpuKernelElement();

  /**
   * Track when a user expands or collapses the cpu kernel view.
   */
  void trackToggleCpuKernelHideablePanel();

  /**
   * Track when a user expands or collapses the cpu threads view.
   */
  void trackToggleCpuThreadsHideablePanel();

  /**
   * Track additional data when a user selects a range while in the energy profiler. Note that this
   * event is sent in addition to a generic range selection event.
   */
  void trackSelectEnergyRange(@NotNull EnergyRangeMetadata rangeMetadata);

  /**
   * Track additional data when a user selects an energy event to see its details.
   */
  void trackSelectEnergyEvent(@NotNull EnergyEventMetadata eventMetadata);

  /**
   * Track when the user selects instance filter for a Memory Profiler's CaptureObject.
   */
  void trackMemoryProfilerInstanceFilter(@NotNull CaptureObjectInstanceFilter filter);

  /**
   * Track an attempt of spawning a new instance of the Trace Processor Daemon and how long it took
   * to either the instance is ready to serve requests (if the spawn was successful) or how long we
   * took to detect that we failed to spawn the new instance.
   */
  void trackTraceProcessorDaemonSpawnAttempt(boolean successful, long timeToSpawnMs);

  /**
   * Track a load trace query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   *                    OK - query returned without issues.
   *                    QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   *                    QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   * @param traceSizeBytes the size in bytes of the trace being loaded in this query.
   */
  void trackTraceProcessorLoadTrace(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs, long traceSizeBytes);

  /**
   * Track a process metadata query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   *                    OK - query returned without issues.
   *                    QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   *                    QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  void trackTraceProcessorProcessMetadata(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs);

  /**
   * Track a cpu data query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   *                    OK - query returned without issues.
   *                    QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   *                    QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  void trackTraceProcessorCpuData(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs);

  /**
   * Track a memory data query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   *                    OK - query returned without issues.
   *                    QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   *                    QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  void trackTraceProcessorMemoryData(
    @NotNull TraceProcessorDaemonQueryStats.QueryReturnStatus queryStatus, long methodTimeMs, long queryTimeMs);

  /**
   * Track when a track group is moved up.
   *
   * @param title track group title, e.g. Display.
   */
  void trackMoveTrackGroupUp(@NotNull String title);

  /**
   * Track when a track group is moved down.
   *
   * @param title track group title, e.g. Display.
   */
  void trackMoveTrackGroupDown(@NotNull String title);

  /**
   * Track when a track group is expanded.
   *
   * @param title track group title, e.g. Display.
   */
  void trackExpandTrackGroup(@NotNull String title);

  /**
   * Track when a track group is collapsed.
   *
   * @param title track group title, e.g. Display.
   */
  void trackCollapseTrackGroup(@NotNull String title);

  /**
   * Track when a box selection is performed.
   *
   * @param durationUs box selection duration in microseconds.
   * @param trackCount number fo tracks included in the selection.
   */
  void trackSelectBox(long durationUs, int trackCount);
}