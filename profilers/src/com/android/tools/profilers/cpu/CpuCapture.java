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

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Map;
import java.util.Set;

public class CpuCapture implements DurationData {

  public static final String MAIN_THREAD_NAME = "main";

  private final int myMainThreadId;

  @NotNull
  private final Map<ThreadInfo, CaptureNode> myCaptureTrees;

  @NotNull
  private Range myRange;

  @NotNull
  private ClockType myClockType;

  public CpuCapture(@NotNull ByteString bytes) {
    // TODO: Remove layers, analyze whether we can keep the whole file in memory.
    try {
      File trace = FileUtil.createTempFile("cpu_trace", ".trace");
      VmTraceData data;
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(bytes.toByteArray());
        VmTraceParser parser = new VmTraceParser(trace);
        parser.parse();
        data = parser.getTraceData();
      }

      CpuTraceArt traceArt = new CpuTraceArt();
      traceArt.parse(data);
      myCaptureTrees = traceArt.getThreadsGraph();
    }
    catch (IOException | BufferUnderflowException e) {
      throw new IllegalStateException(e);
    }

    // Try to find the main thread. The main thread is called "main" but if we fail
    // to find it we will fall back to the thread with the most information.
    Map.Entry<ThreadInfo, CaptureNode> main = null;
    boolean foundMainThread = false;
    myRange = new Range();
    for (Map.Entry<ThreadInfo, CaptureNode> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getName().equals(MAIN_THREAD_NAME)) {
        main = entry;
        foundMainThread = true;
      }
      if (!foundMainThread && (main == null || main.getValue().duration() < entry.getValue().duration())) {
        main = entry;
      }
      myRange.expand(entry.getValue().getStart(), entry.getValue().getEnd());
    }
    // If there is no thread named "main", the trace file is not valid.
    // In this case, we would have caught a BufferUnderflowException from VmTraceParser above and rethrown it as IllegalStateException.
    // If a thread named "main" is not required in the future, we need to double-check the object value for null here instead of asserting.
    assert main != null;
    myMainThreadId = main.getKey().getId();

    // Set clock type
    HNode<MethodModel> mainNode = getCaptureNode(myMainThreadId);
    assert mainNode != null && mainNode instanceof CaptureNode;
    myClockType = ((CaptureNode)mainNode).getClockType();
  }

  public int getMainThreadId() {
    return myMainThreadId;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @Nullable
  public HNode<MethodModel> getCaptureNode(int threadId) {
    for (Map.Entry<ThreadInfo, CaptureNode> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getId() == threadId) {
        return entry.getValue();
      }
    }
    return null;
  }

  @NotNull
  public Set<ThreadInfo> getThreads() {
    return myCaptureTrees.keySet();
  }

  public boolean containsThread(int threadId) {
    return myCaptureTrees.keySet().stream().anyMatch(info -> info.getId() == threadId);
  }

  @Override
  public long getDuration() {
    return (long)myRange.getLength();
  }

  public void updateClockType(@NotNull ClockType clockType) {
    if (myClockType == clockType) {
      // Avoid traversing the capture trees if there is no change.
      return;
    }
    myClockType = clockType;

    for(CaptureNode tree : myCaptureTrees.values()) {
      updateClockType(tree, clockType);
    }
  }

  private static void updateClockType(@Nullable CaptureNode node, @NotNull ClockType clockType) {
    if (node == null) {
      return;
    }
    node.setClockType(clockType);
    for (HNode<MethodModel> child : node.getChildren()) {
      // CpuTraceArt should parse the capture into CaptureNode objects
      assert child instanceof CaptureNode;
      updateClockType((CaptureNode)child, clockType);
    }
  }
}
