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

import com.android.tools.profilers.Stage;
import org.jetbrains.annotations.NotNull;

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

  // TODO: INCREMENTALLY ADD TRACKING FOR REMAINING EVENTS
  //
  /// General
  //
  // CHANGE_DEVICE;
  // CHANGE_PROCESS;
  // GO_BACK;
  // SELECT_MONITOR;
  // ZOOM_IN;
  // ZOOM_OUT;
  // ZOOM_RESET;
  // GO_LIVE;
  // NAVIGATE_TO_CODE;
  // SELECT_RANGE;
  //
  /// CPU
  //
  // TRACE_SAMPLED;
  // TRACE_INSTRUMENTED;
  // SELECT_THREAD;
  // SELECT_TOP_DOWN;
  // SELECT_BOTTOM_UP;
  // SELECT_FLAME_CHART;
  //
  /// Memory
  //
  // FORCE_GC;
  // SNAPSHOT_HPROF;
  // CAPTURE_ALLOCATIONS;
  // SELECT_MEMORY_CHART;
  // EXPORT_HPROF;
  // EXPORT_ALLOCATION;
  // ARRANGE_CLASSES;
  // SELECT_MEMORY_STACK;
  // SELECT_MEMORY_REFERENCES;
  //
  /// Network
  //
  // SELECT_CONNECTION;
  // SELECT_DETAILS_RESPONSE;
  // SELECT_DETAILS_HEADERS;
  // SELECT_DETAILS_STACK;
}
