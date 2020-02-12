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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.ConfigurableDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.cpu.atrace.AtraceFrame;
import com.android.tools.profilers.cpu.atrace.CpuThreadSliceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface represents a CPU trace/capture and all the data accessible from it.
 */
public interface CpuCapture extends ConfigurableDurationData {
  // General Capture information:
  /**
   * Returns the unique id that represents the trace used to generate this capture.
   */
  long getTraceId();

  /**
   * Returns the underlying method/technology used to produce this capture.
   */
  @NotNull
  Cpu.CpuTraceType getType();

  /**
   * Returns the timeline associated with this CpuCapture, which includes the data & view ranges.
   */
  @NotNull
  Timeline getTimeline();

  /**
   * Returns the data range for this capture.
   */
  @NotNull
  default Range getRange() {
    return getTimeline().getDataRange();
  }

  // Multiple Clocks Support
  /**
   * Return true if this capture supports more than one clock type.
   */
  boolean isDualClock();

  /**
   * Update all CaptureNodes in this capture to reference the new {@code clockType}.
   */
  void updateClockType(@NotNull ClockType clockType);


  // Capture Information
  /**
   * Returns the id of the main thread associated with this capture.
   */
  int getMainThreadId();

  /**
   * Returns information on all threads in this capture.
   */
  @NotNull
  Set<CpuThreadInfo> getThreads();

  /**
   * Returns true if the capture contains data for a thread with {@code threadId}.
   */
  // TODO: Remove this, migrate users to getCaptureNode + check the result for null.
  boolean containsThread(int threadId);

  /**
   * Returns the CaptureNode that represents the thread with {@code threadId} or null if such thread isn't present on this capture.
   */
  @Nullable
  CaptureNode getCaptureNode(int threadId);

  /**
   * Return all {@link CaptureNode}s in this capture.
   */
  // TODO (b/138408053): Remove this when we have a proper selection model.
  @NotNull
  Collection<CaptureNode> getCaptureNodes();

  // Extended Capture Information - CPU data
  // These might not be available on all profiling technologies.

  /**
   * Returns the thread state transitions for the given thread.
   * @param threadId Thread Id of thread requesting states for. If thread id is not found an empty list is returned.
   */
  @NotNull
  default List<SeriesData<CpuProfilerStage.ThreadState>> getThreadStatesForThread(int threadId) {
    return new ArrayList<>();
  }

  /**
   * Returns a series of {@link CpuThreadSliceInfo} information.
   * @param cpu The cpu index to get {@link CpuThreadSliceInfo} series for.
   */
  @NotNull
  default List<SeriesData<CpuThreadSliceInfo>> getCpuThreadSliceInfoStates(int cpu) {
    return new ArrayList<>();
  }

  /**
   * Returns multiple CPU Utilization data series, with one for each CPU core present on the traced device.
   */
  @NotNull
  default List<SeriesData<Long>> getCpuUtilizationSeries() {
    return new ArrayList<>();
  }

  /**
   * The number of CPU cores represented in this capture.
   */
  default int getCpuCount() {
    return getCpuUtilizationSeries().size();
  }

  /**
   * Returns true if the capture is potentially missing data. For example, on a ATrace or Perfetto capture,
   * due to the capture buffer being a ring buffer.
   */
  default boolean isMissingData() {
    return false;
  }

  // Extended Capture Information - GPU/Frames data
  // These might not be available on all profiling technologies.

  /**
   * Returns a data series with frame performance classes sorted by frame start time.
   */
  @NotNull
  default List<SeriesData<AtraceFrame>> getFrames(AtraceFrame.FrameThread threadType) {
    return new ArrayList<>();
  }

  /**
   * Returns the thread id of thread matching name of the render thread.
   */
  default int getRenderThreadId() {
    return Integer.MAX_VALUE;
  }

  // Default overrides of ConfigurableDurationData methods for convenience.
  @Override
  default long getDurationUs() {
    return (long) getRange().getLength();
  }

  @Override
  default boolean getSelectableWhenMaxDuration() {
    return false;
  }

  @Override
  default boolean canSelectPartialRange() {
    return true;
  }
}
