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
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.cpu.CpuCaptureMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service for tracking events that occur in our profilers, in order to understand and evaluate
 * how our users are using them.
 *
 * The class that implements this should be sure to let users opt out of sending this information,
 * at which point all these methods should become no-ops.
 */
public interface FeatureTracker {

  /**
   * Track when we enter a new stage. The stage should always be included as state with all other
   * tracking events.
   */
  void trackEnterStage(@NotNull Class<? extends Stage> stage);

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
   * Track the user opening up the CPU profiling configurations dialog.
   */
  void trackOpenProfilingConfigDialog();

  /**
   * Track the user creating custom CPU profiling configurations.
   */
  void trackCreateCustomProfilingConfig();
}
