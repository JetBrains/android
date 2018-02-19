/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profiler.proto.CpuProfiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bidirectional iterator that navigates through the trace IDs of the {@link CpuCapture} generated in the current profiling session.
 */
class TraceIdsIterator {

  static final int INVALID_TRACE_ID = -1;

  private final CpuProfilerStage myStage;

  /**
   * List of trace IDs. Must be sorted by "from" timestamp at all times. We achieve that by sorting the list after building it from the
   * traces existing in the session at the moment we create the stage, and inserting the trace IDs of newly parsed captures to the end of
   * the list.
   */
  private final List<Integer> myTraceIds;

  public TraceIdsIterator(CpuProfilerStage stage, List<CpuProfiler.TraceInfo> initialTraceInfo) {
    myStage = stage;
    myTraceIds = getOrderedInitialTraceIds(initialTraceInfo);
  }

  /**
   * Returns the list of IDs of all the {@link CpuProfiler.TraceInfo} created in the current session, ordered by "from" timestamp.
   */
  private static List<Integer> getOrderedInitialTraceIds(List<CpuProfiler.TraceInfo> initialTraceInfo) {
    // Gets all the trace info of the session and order them per start time. Use a copy of the list because it's immutable.
    List<CpuProfiler.TraceInfo> allTraceInfo = new ArrayList<>(initialTraceInfo);
    allTraceInfo.sort(Comparator.comparingLong(CpuProfiler.TraceInfo::getFromTimestamp));
    List<Integer> traceIds = new ArrayList<>();
    allTraceInfo.forEach((traceInfo) -> traceIds.add(traceInfo.getTraceId()));
    return traceIds;
  }

  public boolean hasNext() {
    return findNextTraceId() != INVALID_TRACE_ID;
  }

  public Integer next() {
    return findNextTraceId();
  }

  /**
   * Returns the trace ID of the next capture in the session, or {@link #INVALID_TRACE_ID} if there is none.
   */
  private int findNextTraceId() {
    if (myTraceIds.isEmpty()) {
      // We can't navigate anywhere.
      return INVALID_TRACE_ID;
    }

    if (myStage.getCapture() == null) {
      // If no capture is selected, we can navigate to the first one.
      return myTraceIds.get(0);
    }

    int currentTraceIdIndex = myTraceIds.indexOf(myStage.getCapture().getTraceId());
    assert currentTraceIdIndex >= 0;

    if (currentTraceIdIndex == myTraceIds.size() - 1) {
      // Current capture is already the last one.
      return INVALID_TRACE_ID;
    }
    return myTraceIds.get(currentTraceIdIndex + 1);
  }

  public boolean hasPrevious() {
    return findPreviousTraceId() != INVALID_TRACE_ID;
  }

  public Integer previous() {
    return findPreviousTraceId();
  }

  /**
   * Returns the trace ID of the previous capture in the session, or {@link #INVALID_TRACE_ID} if there is none.
   */
  private int findPreviousTraceId() {
    if (myTraceIds.isEmpty()) {
      // We don't have a previous capture we can navigate to.
      return INVALID_TRACE_ID;
    }
    if (myStage.getCapture() == null) {
      // If no capture is selected, navigate to the last one.
      return myTraceIds.get(myTraceIds.size() - 1);
    }

    int currentTraceIdIndex = myTraceIds.indexOf(myStage.getCapture().getTraceId());
    assert currentTraceIdIndex >= 0;

    if (currentTraceIdIndex == 0) {
      // Current capture is already the first one. There is no previous.
      return INVALID_TRACE_ID;
    }
    return myTraceIds.get(currentTraceIdIndex - 1);
  }

  /**
   * Adds the given trace to {@link #myTraceIds}.
   */
  public void addTrace(int traceId) {
    myTraceIds.add(traceId);
  }
}
