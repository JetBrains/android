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
import com.android.tools.adtui.model.DefaultTimeline;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Cpu;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuCapture implements ConfigurableDurationData {

  private final int myMainThreadId;

  @NotNull
  private ClockType myClockType;

  @NotNull
  private final Map<CpuThreadInfo, CaptureNode> myCaptureTrees;

  /**
   * The CPU capture has its own {@link Timeline} for the purpose of exposing a variety of {@link Range}s.
   */
  @NotNull
  private final Timeline myTimeline = new DefaultTimeline();

  private final boolean myCaptureSupportsDualClock;

  /**
   * ID of the trace used to generate the capture.
   */
  private final long myTraceId;

  /**
   * Technology used to generate the capture.
   */
  private final Cpu.CpuTraceType myType;

  public CpuCapture(@NotNull TraceParser parser, long traceId, Cpu.CpuTraceType type) {
    myTraceId = traceId;
    myType = type;
    myTimeline.getDataRange().set(parser.getRange());
    myTimeline.getViewRange().set(parser.getRange());
    myCaptureSupportsDualClock = parser.supportsDualClock();

    // Sometimes a capture may fail and return a file that is incomplete. This results in the parser not having any capture trees.
    // If this happens then we don't have any thread info to determine which is the main thread
    // so we throw an error and let the capture pipeline handle this and present a dialog to the user.
    myCaptureTrees = parser.getCaptureTrees();
    if (myCaptureTrees.isEmpty()) {
      throw new IllegalStateException("Trace file contained no CPU data.");
    }

    // Try to find the main thread. If there is no actual main thread, we will fall back to the thread with the most information.
    Map.Entry<CpuThreadInfo, CaptureNode> main = null;
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().isMainThread()) {
        main = entry;
        break;
      }

      if (main == null || main.getValue().getDuration() < entry.getValue().getDuration()) {
        main = entry;
      }
    }

    myMainThreadId = main.getKey().getId();

    // Set clock type
    CaptureNode mainNode = getCaptureNode(myMainThreadId);
    assert mainNode != null;
    myClockType = mainNode.getClockType();
  }

  public int getMainThreadId() {
    return myMainThreadId;
  }

  /**
   * @return a timeline that uses the capture range as its data & view range.
   */
  @NotNull
  public Timeline getTimeline() {
    return myTimeline;
  }

  @NotNull
  public Range getRange() {
    return myTimeline.getDataRange();
  }

  @Nullable
  public CaptureNode getCaptureNode(int threadId) {
    return myCaptureTrees.entrySet().stream()
      .filter(e -> e.getKey().getId() == threadId)
      .findFirst()
      .map(Map.Entry::getValue)
      .orElse(null);
  }

  @NotNull
  Set<CpuThreadInfo> getThreads() {
    return myCaptureTrees.keySet();
  }

  // TODO (b/138408053): Remove this when we have a proper selection model.
  @NotNull
  public Collection<CaptureNode> getCaptureNodes() {
    return myCaptureTrees.values();
  }

  public boolean containsThread(int threadId) {
    return getCaptureNode(threadId) != null;
  }

  @Override
  public long getDurationUs() {
    return (long)getRange().getLength();
  }

  @Override
  public boolean getSelectableWhenMaxDuration() {
    return false;
  }

  public long getTraceId() {
    return myTraceId;
  }

  @Override
  public boolean canSelectPartialRange() {
    return true;
  }

  public void updateClockType(@NotNull ClockType clockType) {
    if (myClockType == clockType) {
      // Avoid traversing the capture trees if there is no change.
      return;
    }
    myClockType = clockType;

    for (CaptureNode tree : getCaptureNodes()) {
      updateClockType(tree, clockType);
    }
  }

  private static void updateClockType(@Nullable CaptureNode node, @NotNull ClockType clockType) {
    if (node == null) {
      return;
    }
    node.setClockType(clockType);
    for (CaptureNode child : node.getChildren()) {
      updateClockType(child, clockType);
    }
  }

  public boolean isDualClock() {
    return myCaptureSupportsDualClock;
  }

  public Cpu.CpuTraceType getType() {
    return myType;
  }
}
