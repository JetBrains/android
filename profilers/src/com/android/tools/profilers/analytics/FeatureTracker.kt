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
package com.android.tools.profilers.analytics

import com.android.tools.analytics.CommonMetricsData
import com.android.tools.analytics.HostData
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profiler.proto.Cpu.CpuTraceType
import com.android.tools.profilers.analytics.energy.EnergyEventMetadata
import com.android.tools.profilers.analytics.energy.EnergyRangeMetadata
import com.android.tools.profilers.cpu.CpuCaptureMetadata
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager.SessionCreationSource
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import com.google.wireless.android.sdk.stats.RunWithProfilingMetadata
import com.google.wireless.android.sdk.stats.TraceProcessorDaemonQueryStats.QueryReturnStatus

/**
 * A service for tracking events that occur in our profilers, in order to understand and evaluate
 * how our users are using them.
 *
 *
 * The class that implements this should be sure to let users opt out of sending this information,
 * at which point all these methods should become no-ops.
 */
interface FeatureTracker {
  /**
   * Track when the TransportDeviceManager detects an ddmlib device and is about to begin the logic
   * to launch the transport daemon.
   */
  fun trackPreTransportDaemonStarts(transportDevice: Common.Device)

  /**
   * Track when the transport daemon fails to launch.
   */
  fun trackTransportDaemonFailed(transportDevice: Common.Device, exception: Exception)

  /**
   * Track when the transport proxy fails to connect the datastore and the daemon server.
   */
  fun trackTransportProxyCreationFailed(transportDevice: Common.Device, exception: Exception)

  /**
   * Track when the profilers failed to initiailize. This happens when the ProfilerService is
   * unavailable. (e.g. more than one Studio instance access profilers)
   */
  fun trackProfilerInitializationFailed()

  /**
   * Track when we enter a new stage. The stage should always be included as state with all other
   * tracking events.
   */
  fun trackEnterStage(stageType: AndroidProfilerEvent.Stage)

  /**
   * Track when the user clicks the Profile button. This can happen via the tool bar, or the run menu.
   * This is in contrast to when the user attaches profilers to an app that was already running.
   *
   * Can optionally set metadata like profiling mode, etc.
   */
  fun trackRunWithProfiling(metadata: RunWithProfilingMetadata)

  /**
   * Track when auto profiling is requested. e.g. when the user clicks "Profile", or "Run"/"Debug"
   * while the profilers window is opened.
   */
  fun trackAutoProfilingRequested()

  /**
   * Track when auto profiling has found the matching process.
   */
  fun trackAutoProfilingSucceeded()

  /**
   * Track when we begin profiling a target process.
   */
  fun trackProfilingStarted()

  /**
   * Track when we learn that the process we are profiling is instrumented and allows us to query
   * advanced profiling information. This value will always be a subset of
   * [.trackProfilingStarted].
   */
  fun trackAdvancedProfilingStarted()

  /**
   * Track when the user takes an action to change the current device. This will only be tracked
   * if the device actually changes.
   */
  fun trackChangeDevice(device: Common.Device)

  /**
   * Track when the user takes an action to change the current process. This will only be tracked
   * if the process actually changes.
   */
  fun trackChangeProcess(process: Common.Process)

  /**
   * Track when user presses the "+" button in the Sessions panel.
   */
  fun trackSessionDropdownClicked()

  /**
   * Track when the user explicitly creates a new session via the Sessions UI.
   */
  fun trackCreateSession(sessionType: SessionMetaData.SessionType, sourceType: SessionCreationSource)

  /**
   * Track when the user explicitly stops an ongoing profiling session, without starting a new one (e.g. selecting a new process).
   */
  fun trackStopSession()

  /**
   * Track when the user toggles the Sessions panel.
   */
  fun trackSessionsPanelStateChanged(isExpanded: Boolean)

  /**
   * Track when the user resizes the Sessions panel. This is only applicable in the expanded view.
   */
  fun trackSessionsPanelResized()

  /**
   * Track when the user selects a session item in the UI, and whether the associated session is currently active.
   */
  fun trackSessionArtifactSelected(artifact: SessionArtifact<*>, isSessionLive: Boolean)

  /**
   * Track when the user takes an action to return back to the top-level monitor view (from a
   * specific profiler).
   */
  fun trackGoBack()

  /**
   * Track when the user takes an action to change to a new monitor.
   */
  fun trackSelectMonitor()

  /**
   * Track when the user takes an action to zoom in one level.
   */
  fun trackZoomIn()

  /**
   * Track when the user takes an action to zoom out one level.
   */
  fun trackZoomOut()

  /**
   * Track when the user takes an action to restore zoom to its default level.
   */
  fun trackResetZoom()

  /**
   * Track when the user takes an action to zoom to the current selection.
   */
  fun trackZoomToSelection()

  /**
   * Track the user toggling whether the profiler should stream or not.
   */
  fun trackToggleStreaming()

  /**
   * Track the user navigating away from the profiler to some target source code.
   */
  fun trackNavigateToCode()

  /**
   * Track anytime the user creates a range selection in any of our charts.
   */
  fun trackSelectRange()

  /**
   * Track the user capturing a method trace.
   */
  fun trackCaptureTrace(cpuCaptureMetadata: CpuCaptureMetadata)

  /**
   * Track the user importing a method trace.
   */
  fun trackImportTrace(traceType: CpuTraceType, success: Boolean)

  /**
   * Track the startup CPU profiling that was started with the given {@param device} and {@param configuration}.
   */
  fun trackCpuStartupProfiling(device: Common.Device, configuration: ProfilingConfiguration)

  /**
   * @param sampling     True if using sampling; false if using instrumentation.
   * @param pathProvided A trace path is given and not null (we don't log the path as it might contain PII).
   * @param bufferSize   Buffer size as a given API argument (-1 if unavailable).
   * @param flags        Flags as a given API argument (-1 if unavailable).
   * @param intervalUs   Sampling interval as a given API argument (-1 if unavailable).
   */
  fun trackCpuApiTracing(sampling: Boolean, pathProvided: Boolean, bufferSize: Int, flags: Int, intervalUs: Int)

  /**
   * Track the user clicking on one of the threads in the thread list.
   */
  fun trackSelectThread()

  /**
   * Track the user opening up the "Top Down" tab in the CPU capture view
   */
  fun trackSelectCaptureTopDown()

  /**
   * Track the user opening up the "Bottom Up" tab in the CPU capture view
   */
  fun trackSelectCaptureBottomUp()

  /**
   * Track the user opening up the "Flame Chart" tab in the CPU capture view
   */
  fun trackSelectCaptureFlameChart()

  /**
   * Track when the user requests memory be garbage collected.
   */
  fun trackForceGc()

  /**
   * Track when the user takes a snapshot of the memory heap.
   */
  fun trackDumpHeap()

  /**
   * Track when user finishes recording memory allocations
   */
  fun trackRecordAllocations()

  /**
   * Track when the user exports a heap snapshot.
   * TODO: This needs to be hooked up.
   */
  fun trackExportHeap()

  /**
   * Track when the user exports an allocation recording.
   * TODO: This needs to be hooked up.
   */
  fun trackExportAllocation()

  /**
   * Track when the user changes the class-arrangement strategy.
   * TODO: This needs to be hooked up.
   */
  fun trackChangeClassArrangment()

  /**
   * Track the user opening up the "Stack" tab in the memory details view.
   */
  fun trackSelectMemoryStack()

  /**
   * Track the user opening up the "Reference" tab in the memory details view.
   */
  fun trackSelectMemoryReferences()

  /**
   * Track the user selecting a heap in the memory heap combobox.
   */
  fun trackSelectMemoryHeap(heapName: String)

  /**
   * Track the user selecting a row from a table of connections.
   */
  fun trackSelectNetworkRequest()

  /**
   * Track the user opening up the "Overview" tab in the network details view.
   */
  fun trackSelectNetworkDetailsOverview()

  /**
   * Track the user opening up the "Response" tab in the network details view.
   */
  fun trackSelectNetworkDetailsResponse()

  /**
   * Track the user opening up the "Request" tab in the network details view.
   */
  fun trackSelectNetworkDetailsRequest()

  /**
   * Track the user opening up the "Trace" tab in the network details view.
   */
  fun trackSelectNetworkDetailsStack()

  /**
   * Track the user selecting the "Connections View" tab.
   */
  fun trackSelectNetworkConnectionsView()

  /**
   * Track the user selecting the "Threads View" tab.
   */
  fun trackSelectNetworkThreadsView()

  /**
   * Track the user opening up the CPU profiling configurations dialog.
   */
  fun trackOpenProfilingConfigDialog()

  /**
   * Track the user creating custom CPU profiling configurations.
   */
  fun trackCreateCustomProfilingConfig()

  /**
   * Track when the user uses the filter component in the profilers.
   */
  fun trackFilterMetadata(filterMetadata: FilterMetadata)

  /**
   * Track when a user expands or collapses the cpu threads view.
   */
  fun trackToggleCpuThreadsHideablePanel()

  /**
   * Track additional data when a user selects a range while in the energy profiler. Note that this
   * event is sent in addition to a generic range selection event.
   */
  fun trackSelectEnergyRange(rangeMetadata: EnergyRangeMetadata)

  /**
   * Track additional data when a user selects an energy event to see its details.
   */
  fun trackSelectEnergyEvent(eventMetadata: EnergyEventMetadata)

  /**
   * Track when the user selects instance filter for a Memory Profiler's CaptureObject.
   */
  fun trackMemoryProfilerInstanceFilter(filter: CaptureObjectInstanceFilter)

  /**
   * Track an attempt of spawning a new instance of the Trace Processor Daemon and how long it took
   * to either the instance is ready to serve requests (if the spawn was successful) or how long we
   * took to detect that we failed to spawn the new instance.
   */
  fun trackTraceProcessorDaemonSpawnAttempt(successful: Boolean, timeToSpawnMs: Long)

  /**
   * Track a load trace query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   * OK - query returned without issues.
   * QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   * QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   * @param traceSizeBytes the size in bytes of the trace being loaded in this query.
   */
  fun trackTraceProcessorLoadTrace(
    queryStatus: QueryReturnStatus, methodTimeMs: Long, queryTimeMs: Long, traceSizeBytes: Long
  )

  /**
   * Track a process metadata query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   * OK - query returned without issues.
   * QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   * QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  fun trackTraceProcessorProcessMetadata(
    queryStatus: QueryReturnStatus, methodTimeMs: Long, queryTimeMs: Long
  )

  /**
   * Track a cpu data query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   * OK - query returned without issues.
   * QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   * QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  fun trackTraceProcessorCpuData(
    queryStatus: QueryReturnStatus, methodTimeMs: Long, queryTimeMs: Long
  )

  /**
   * Track a memory data query sent to the Trace Processor Daemon.
   *
   * @param queryStatus tracks the status of the query result:
   * OK - query returned without issues.
   * QUERY_ERROR - query returned but TPD identified some issues while processing the query.
   * QUERY_FAIL - query failed to reach TPD.
   * @param methodTimeMs how long - in milliseconds - we spent in the whole method (query + business logic).
   * @param queryTimeMs  how long - in milliseconds - we spent waiting for the query to return from TPD.
   */
  fun trackTraceProcessorMemoryData(
    queryStatus: QueryReturnStatus, methodTimeMs: Long, queryTimeMs: Long
  )

  /**
   * Track when a track group is moved up.
   *
   * @param title track group title, e.g. Display.
   */
  fun trackMoveTrackGroupUp(title: String)

  /**
   * Track when a track group is moved down.
   *
   * @param title track group title, e.g. Display.
   */
  fun trackMoveTrackGroupDown(title: String)

  /**
   * Track when a track group is expanded.
   *
   * @param title track group title, e.g. Display.
   */
  fun trackExpandTrackGroup(title: String)

  /**
   * Track when a track group is collapsed.
   *
   * @param title track group title, e.g. Display.
   */
  fun trackCollapseTrackGroup(title: String)

  /**
   * Track when a box selection is performed.
   *
   * @param durationUs box selection duration in microseconds.
   * @param trackCount number fo tracks included in the selection.
   */
  fun trackSelectBox(durationUs: Long, trackCount: Int)
  // TODO(b/188695273): to be removed.
  /**
   * Tracks when the user clicks on the network profiler migration dialog.
   */
  fun trackNetworkMigrationDialogSelected()

  /**
   * Tracks when the user has performed this many frame selections within one session viewing a trace
   */
  fun trackFrameSelectionPerTrace(count: Int)

  /**
   * Tracks when the user has toggled the "All Frames" checkbox this many times within one session viewing a trace
   */
  fun trackAllFrameTogglingPerTrace(count: Int)

  /**
   * Tracks when the user has toggled the "Lifecycle" checkbox this many times within one session viewing a trace
   */
  fun trackLifecycleTogglingPerTrace(count: Int)

  /**
   * Tracks the loading of a trace or file
   */
  fun trackLoading(loading: AndroidProfilerEvent.Loading)
}

/**
 * Run the task, with tracking before and after upon success.
 * @param sizeKb the size of the trace/file in KB, should be available without running
 * @param measure the size of the in-memory representation (e.g. object count, event count, etc.)
 *                that's only queried if the task succeeds
 */
fun <A> FeatureTracker.trackLoading(type: AndroidProfilerEvent.Loading.Type, sizeKb: Int, measure: () -> Long, run: () -> A): A {
  fun Long.bToMb() = this / (1024 * 1024)
  val totalMem = HostData.osBean?.totalPhysicalMemorySize?.bToMb() ?: 0L
  val numProcs = HostData.osBean?.availableProcessors ?: 0
  val studioMem = CommonMetricsData.jvmDetails.maximumHeapSize.bToMb()
  val studioFree = (CommonMetricsData.jvmDetails.maximumHeapSize -
                    CommonMetricsData.javaProcessStats.heapMemoryUsage).bToMb()

  fun track(setUp: AndroidProfilerEvent.Loading.Builder.() -> Unit) =
    trackLoading(AndroidProfilerEvent.Loading.newBuilder()
                   .setType(type)
                   .setSizeKb(sizeKb)
                   .setCoreCount(numProcs)
                   .setMachineMemoryMb(totalMem.toInt())
                   .setStudioMaxMemoryMb(studioMem.toInt())
                   .setStudioFreeMemoryMb(studioFree.toInt())
                   .apply(setUp)
                   .build())

  track { isSuccess = false }
  val t1 = System.currentTimeMillis()
  val res = run()
  val t2 = System.currentTimeMillis()
  track { isSuccess = true; elapsedMs = (t2 - t1).toInt(); eventCount = measure() }
  return res
}