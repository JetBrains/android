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
import com.android.tools.adtui.model.Timeline;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.systemtrace.CpuSystemTraceData;
import java.util.Collection;
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
  Trace.TraceType getType();

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
   * @return a message of why dual clock isn't supported. Can be null if there's nothing to say.
   * The message may be surfaced to the user by the front end.
   */
  @Nullable
  String getDualClockDisabledMessage();

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

  /**
   * Returns the container for all system trace data available in this capture
   * or null if system trace data is not available on this capture.
   */
  @Nullable
  default CpuSystemTraceData getSystemTraceData() {
    return null;
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

  /**
   * Updates the set of tags whose corresponding nodes should be hidden
   */
  void collapseNodesWithTags(@NotNull Set<String> tagsToCollapse);
  @NotNull Set<String> getCollapsedTags();
  @NotNull Set<String> getTags();
}
