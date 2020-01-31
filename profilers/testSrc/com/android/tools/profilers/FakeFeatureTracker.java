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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.analytics.FilterMetadata;
import com.android.tools.profilers.analytics.energy.EnergyEventMetadata;
import com.android.tools.profilers.analytics.energy.EnergyRangeMetadata;
import com.android.tools.profilers.cpu.CpuCaptureMetadata;
import com.android.tools.profilers.cpu.ProfilingConfiguration;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.sessions.SessionArtifact;
import com.android.tools.profilers.sessions.SessionsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FakeFeatureTracker implements FeatureTracker {

  /**
   * Stores the last {@link CpuCaptureMetadata} passed to the tracker.
   */
  private CpuCaptureMetadata myLastCpuCaptureMetadata;

  /**
   * Stores the last {@link EnergyEventMetadata} passed to the tracker.
   */
  private EnergyEventMetadata myLastEnergyEventMetadata;

  /**
   * Stores the last {@link EnergyRangeMetadata} passed to the tracker.
   */
  private EnergyRangeMetadata myLastEnergyRangeMetadata;

  /**
   * Stores the last {@link FilterMetadata} passed to the tracker.
   */
  private FilterMetadata myLastFilterMetadata;

  /**
   * Stores the last {@link Cpu.CpuTraceType} passed to the tracker.
   */
  public Cpu.CpuTraceType myLastCpuTraceType;

  /**
   * Whether the last import trace was tracked as success.
   */
  private Boolean myLastImportTraceSucceeded;

  /**
   * Whether {@link #trackSelectThread()} was called.
   */
  private boolean myTrackSelectThreadCalled;

  /**
   * Stores the last boolean value of {@link pathProvided} passed to track API tracing.
   */
  private boolean myLastCpuApiTracingPathProvided;

  /**
   * The times that the usage data of API-initiated tracing has been recorded.
   */
  private int myApiTracingUsageCount = 0;

  /**
   * The last {@link CaptureDetails} passed to the tracker.
   */
  @Nullable private CaptureDetails.Type myLastCaptureDetailsType = null;

  @Override
  public void trackPreTransportDaemonStarts(@NotNull Common.Device transportDevice) {

  }

  @Override
  public void trackTransportDaemonFailed(@NotNull Common.Device transportDevice, Exception exception) {

  }

  @Override
  public void trackTransportProxyCreationFailed(@NotNull Common.Device transportDevice, Exception exception) {

  }

  @Override
  public void trackProfilerInitializationFailed() {

  }

  @Override
  public void trackEnterStage(@NotNull Class<? extends Stage> stage) {

  }

  @Override
  public void trackRunWithProfiling() {

  }

  @Override
  public void trackAutoProfilingRequested() {

  }

  @Override
  public void trackAutoProfilingSucceeded() {

  }

  @Override
  public void trackProfilingStarted() {

  }

  @Override
  public void trackAdvancedProfilingStarted() {

  }

  @Override
  public void trackChangeDevice(@Nullable Common.Device device) {

  }

  @Override
  public void trackChangeProcess(@Nullable Common.Process process) {

  }

  @Override
  public void trackSessionDropdownClicked() {

  }

  @Override
  public void trackCreateSession(Common.SessionMetaData.SessionType sessionType, SessionsManager.SessionCreationSource sourceType) {

  }

  @Override
  public void trackStopSession() {

  }

  @Override
  public void trackSessionsPanelStateChanged(boolean isExpanded) {

  }

  @Override
  public void trackSessionsPanelResized() {

  }

  @Override
  public void trackSessionArtifactSelected(@NotNull SessionArtifact artifact, boolean isSessionLive) {

  }

  @Override
  public void trackGoBack() {

  }

  @Override
  public void trackSelectMonitor() {

  }

  @Override
  public void trackZoomIn() {

  }

  @Override
  public void trackZoomOut() {

  }

  @Override
  public void trackResetZoom() {

  }

  @Override
  public void trackToggleStreaming() {

  }

  @Override
  public void trackNavigateToCode() {

  }

  @Override
  public void trackSelectRange() {

  }

  @Override
  public void trackCaptureTrace(@NotNull CpuCaptureMetadata cpuCaptureMetadata) {
    myLastCpuCaptureMetadata = cpuCaptureMetadata;
  }

  public CpuCaptureMetadata getLastCpuCaptureMetadata() {
    return myLastCpuCaptureMetadata;
  }

  @Override
  public void trackImportTrace(@NotNull Cpu.CpuTraceType profilerType, boolean success) {
    myLastCpuTraceType = profilerType;
    myLastImportTraceSucceeded = success;
  }

  public Cpu.CpuTraceType getLastCpuTraceType() {
    return myLastCpuTraceType;
  }

  public Boolean getLastImportTraceStatus() {
    return myLastImportTraceSucceeded;
  }

  @Override
  public void trackCpuStartupProfiling(@NotNull Common.Device device, @NotNull ProfilingConfiguration configuration) {
  }

  @Override
  public void trackCpuApiTracing(boolean sampling, boolean pathProvided, int bufferSize, int flags, int intervalUs) {
    myLastCpuApiTracingPathProvided = pathProvided;
    myApiTracingUsageCount++;
  }

  public boolean getLastCpuAPiTracingPathProvided() {
    return myLastCpuApiTracingPathProvided;
  }

  public int getApiTracingUsageCount() {
    return myApiTracingUsageCount;
  }

  @Override
  public void trackSelectThread() {
    myTrackSelectThreadCalled = true;
  }

  public boolean isTrackSelectThreadCalled() {
    return myTrackSelectThreadCalled;
  }

  @Override
  public void trackSelectCaptureTopDown() {
    myLastCaptureDetailsType = CaptureDetails.Type.TOP_DOWN;
  }

  @Override
  public void trackSelectCaptureBottomUp() {
    myLastCaptureDetailsType = CaptureDetails.Type.BOTTOM_UP;
  }

  @Override
  public void trackSelectCaptureFlameChart() {
    myLastCaptureDetailsType = CaptureDetails.Type.FLAME_CHART;
  }

  @Override
  public void trackSelectCaptureCallChart() {
    myLastCaptureDetailsType = CaptureDetails.Type.CALL_CHART;
  }

  public void resetLastCaptureDetailsType() {
    myLastCaptureDetailsType = null;
  }

  @Nullable
  public CaptureDetails.Type getLastCaptureDetailsType() {
    return myLastCaptureDetailsType;
  }

  @Override
  public void trackForceGc() {

  }

  @Override
  public void trackDumpHeap() {

  }

  @Override
  public void trackRecordAllocations() {

  }

  @Override
  public void trackExportHeap() {

  }

  @Override
  public void trackExportAllocation() {

  }

  @Override
  public void trackChangeClassArrangment() {

  }

  @Override
  public void trackSelectMemoryStack() {

  }

  @Override
  public void trackSelectMemoryReferences() {

  }

  @Override
  public void trackSelectMemoryHeap(@NotNull String heapName) {
  }

  @Override
  public void trackSelectNetworkRequest() {

  }

  @Override
  public void trackSelectNetworkDetailsOverview() {

  }

  @Override
  public void trackSelectNetworkDetailsHeaders() {

  }

  @Override
  public void trackSelectNetworkDetailsResponse() {

  }

  @Override
  public void trackSelectNetworkDetailsRequest() {

  }

  @Override
  public void trackSelectNetworkDetailsStack() {

  }

  @Override
  public void trackSelectNetworkDetailsError() {

  }

  @Override
  public void trackSelectNetworkConnectionsView() {

  }

  @Override
  public void trackSelectNetworkThreadsView() {

  }

  @Override
  public void trackOpenProfilingConfigDialog() {

  }

  @Override
  public void trackCreateCustomProfilingConfig() {

  }

  @Override
  public void trackSelectCpuKernelElement() {

  }

  @Override
  public void trackToggleCpuKernelHideablePanel() {

  }

  @Override
  public void trackToggleCpuThreadsHideablePanel() {

  }

  @Override
  public void trackFilterMetadata(@NotNull FilterMetadata filterMetadata) {
    myLastFilterMetadata = filterMetadata;
  }

  @Override
  public void trackSelectEnergyRange(@NotNull EnergyRangeMetadata rangeMetadata) {
    myLastEnergyRangeMetadata = rangeMetadata;
  }

  @Override
  public void trackMemoryProfilerInstanceFilter(@NotNull CaptureObjectInstanceFilter filter) {
  }

  public EnergyRangeMetadata getLastEnergyRangeMetadata() {
    return myLastEnergyRangeMetadata;
  }

  @Override
  public void trackSelectEnergyEvent(@NotNull EnergyEventMetadata eventMetadata) {
    myLastEnergyEventMetadata = eventMetadata;
  }

  public EnergyEventMetadata getLastEnergyEventMetadata() {
    return myLastEnergyEventMetadata;
  }

  public FilterMetadata getLastFilterMetadata() {
    return myLastFilterMetadata;
  }
}
