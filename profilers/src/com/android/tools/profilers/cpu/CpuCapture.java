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
import com.android.tools.perflib.vmtrace.ClockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class CpuCapture implements ConfigurableDurationData {

  public static final String MAIN_THREAD_NAME = "main";

  private final int myMainThreadId;

  @NotNull
  private final Map<CpuThreadInfo, CaptureNode> myCaptureTrees;

  @NotNull
  private Range myRange;

  @NotNull
  private ClockType myClockType;

  /**
   * Whether the capture supports dual clock, i.e. if the method trace data can be displayed in both thread and wall-clock time.
   */
  private boolean myDualClock;

  public CpuCapture(@NotNull Range captureRange, @NotNull Map<CpuThreadInfo, CaptureNode> captureTrees, boolean isDualClock) {
    myRange = captureRange;
    myCaptureTrees = captureTrees;
    myDualClock = isDualClock;

    // Try to find the main thread. The main thread is called "main" but if we fail
    // to find it we will fall back to the thread with the most information.
    Map.Entry<CpuThreadInfo, CaptureNode> main = null;
    boolean foundMainThread = false;
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : captureTrees.entrySet()) {
      if (entry.getKey().getName().equals(MAIN_THREAD_NAME)) {
        main = entry;
        foundMainThread = true;
      }
      if (!foundMainThread && (main == null || main.getValue().getDuration() < entry.getValue().getDuration())) {
        main = entry;
      }
    }
    // If there is no thread named "main", the trace file is not valid.
    // In this case, we would have caught a BufferUnderflowException from VmTraceParser above and rethrown it as IllegalStateException.
    // If a thread named "main" is not required in the future, we need to double-check the object value for null here instead of asserting.
    assert main != null;
    myMainThreadId = main.getKey().getId();

    // Set clock type
    CaptureNode mainNode = getCaptureNode(myMainThreadId);
    assert mainNode != null;
    myClockType = mainNode.getClockType();
  }

  public int getMainThreadId() {
    return myMainThreadId;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @Nullable
  public CaptureNode getCaptureNode(int threadId) {
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getId() == threadId) {
        return entry.getValue();
      }
    }
    return null;
  }

  @NotNull
  Set<CpuThreadInfo> getThreads() {
    return myCaptureTrees.keySet();
  }

  public boolean containsThread(int threadId) {
    return myCaptureTrees.keySet().stream().anyMatch(info -> info.getId() == threadId);
  }

  @Override
  public long getDuration() {
    return (long)myRange.getLength();
  }

  @Override
  public boolean getSelectableWhenMaxDuration() {
    return false;
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

    for (CaptureNode tree : myCaptureTrees.values()) {
      updateClockType(tree, clockType);
    }
  }

  private static void updateClockType(@Nullable CaptureNode node, @NotNull ClockType clockType) {
    if (node == null) {
      return;
    }
    node.setClockType(clockType);
    for (CaptureNode child : node.getChildren()) {
      // CpuTraceArt should parse the capture into CaptureNode objects
      updateClockType(child, clockType);
    }
  }

  public boolean isDualClock() {
    return myDualClock;
  }
}
