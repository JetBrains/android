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
package com.android.tools.idea.monitor.ui.cpu.model;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.chart.hchart.Method;
import com.android.tools.adtui.chart.hchart.Separators;
import com.android.tools.perflib.vmtrace.*;
import com.android.utils.SparseArray;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TraceArt extends AppTrace {

  // Raw trace as it is generated from ART
  private final File myTraceFile;

  // Tree representation of ART trace (generated from perflib tree).
  // Suitable for Rendition with HTreelib.
  SparseArray<HNode<Method>> myNodes;

  // Tree representation of ART trace (generated from raw trace).
  private VmTraceData myData;

  public TraceArt(File traceFile) {
    myTraceFile = traceFile;
  }

  @Override
  public Source getSource() {
    return Source.ART;
  }

  @Override
  public String getSeparator() {
    return Separators.JAVA_CODE;
  }

  @Override
  public void parse() throws IOException {
    // Parse raw myData into perflib representation.
    VmTraceParser parser = new VmTraceParser(this.myTraceFile);
    parser.parse();
    myData = parser.getTraceData();
    myNodes = new SparseArray<>();

    // Convert perlib tree to Hnode tree.
    for (ThreadInfo threadInfo : myData.getThreads()) {

      if (threadInfo.getTopLevelCall() == null || threadInfo.getTopLevelCall().getCallees().size() == 0) {
        continue;
      }

      // ART stores the name of the thread in the first node. We need to skip it.
      Call firstCall = threadInfo.getTopLevelCall().getCallees().get(0);
      HNode<Method> root = new HNode();

      // ART stores timesamp in a compressed fashion: All timestamp are 32 bits relative to a startTime.
      // We need to reconstruct the full timestamp by adding each of them to startTime.
      root.setStart(firstCall.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs());
      root.setEnd((firstCall.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      myNodes.put(threadInfo.getId(), root);
      convertCallsToNode(Arrays.asList(firstCall), root, 0);
    }
  }

  // Convert perflib |calls| to HNodes and add them to |root|.
  private void convertCallsToNode(List<Call> calls, HNode root, int depth) {
    for (Call c : calls) {
      HNode<Method> node = new HNode();
      node.setStart((c.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      node.setEnd((c.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      node.setDepth(depth);

      Method method = new Method();
      method.setName(myData.getMethod(c.getMethodId()).methodName);
      method.setNamespace(myData.getMethod(c.getMethodId()).className);
      node.setData(method);

      root.addHNode(node);

      // Recursion
      convertCallsToNode(c.getCallees(), node, depth + 1);
    }

  }

  @Override
  public SparseArray<HNode<Method>> getThreadsGraph() {
    return myNodes;
  }
}
