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

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedTreeModel;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.AspectModel;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class CpuCapture extends AspectModel<CpuCaptureAspect> {

  public static final String MAIN_THREAD_NAME = "main";

  private final int myMainThreadId;

  @Nullable
  private HNode<MethodModel> myCaptureNode;

  @NotNull
  private final Map<ThreadInfo, HNode<MethodModel>> myCaptureTrees;

  @NotNull
  private final Range myRange;
  private final VmTraceData myData;

  public CpuCapture(@NotNull CpuProfiler.CpuProfilingAppStopResponse response) {

    // TODO: Move file parsing/manipulation to another thread.
    try {
      File trace = FileUtil.createTempFile("cpu_trace", ".trace");
      try (FileOutputStream out = new FileOutputStream(trace)) {
        out.write(response.getTrace().toByteArray());
        VmTraceParser parser = new VmTraceParser(trace);
        parser.parse();
        myData = parser.getTraceData();
      }


      CpuTraceArt traceArt = new CpuTraceArt();
      traceArt.parse(myData);
      myCaptureTrees = traceArt.getThreadsGraph();
    }
    catch (IOException e) {
      throw new IllegalStateException(e);
    }

    // Try to find the main thread. The main thread is called "main" but if we fail
    // to find it we will fall back to the thread with the most information.
    Map.Entry<ThreadInfo, HNode<MethodModel>> main = null;
    for (Map.Entry<ThreadInfo, HNode<MethodModel>> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getName().equals(MAIN_THREAD_NAME)) {
        main = entry;
        break;
      }
      if (main == null || main.getValue().duration() < entry.getValue().duration()) {
        main = entry;
      }
    }
    if (main == null) {
      throw new IllegalArgumentException("Invalid trace");
    }
    myMainThreadId = main.getKey().getId();
    myCaptureNode = main.getValue();
    myRange = new Range(myCaptureNode.getStart(), myCaptureNode.getEnd());
  }

  public int getMainThreadId() {
    return myMainThreadId;
  }

  public Range getRange() {
    return myRange;
  }

  public void setSelectedThread(int id) {
    for (Map.Entry<ThreadInfo, HNode<MethodModel>> entry : myCaptureTrees.entrySet()) {
      if (entry.getKey().getId() == id) {
        myCaptureNode = entry.getValue();
        changed(CpuCaptureAspect.CAPTURE_THREAD);
        return;
      }
    }
    myCaptureNode = null;
    changed(CpuCaptureAspect.CAPTURE_THREAD);
  }

  @Nullable
  public HNode<MethodModel> getCaptureNode() {
    return myCaptureNode;
  }

  public RangedTreeModel getTopDown() {
    return new TopDownTreeModel(new TopDownNode(myCaptureNode));
  }
}
