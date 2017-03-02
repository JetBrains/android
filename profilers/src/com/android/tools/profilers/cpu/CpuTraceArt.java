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
import com.android.tools.perflib.vmtrace.Call;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CpuTraceArt {

  /**
   * Tree representation of ART trace (generated from perflib tree).
   * Keys are thread ids and values are their respective {@link HNode}
   */
  Map<ThreadInfo, CaptureNode> myNodes;

  public void parse(VmTraceData data) throws IOException {
    myNodes = new HashMap<>();

    // Convert perflib tree to HNode tree.
    for (ThreadInfo threadInfo : data.getThreads()) {
      if (threadInfo.getTopLevelCall() == null) {
        continue;
      }
      Call topLevelCall = threadInfo.getTopLevelCall();

      long topLevelGlobalStart = topLevelCall.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + data.getStartTimeUs();
      myNodes.put(threadInfo, convertCallsToNode(data, topLevelCall, 0, topLevelGlobalStart));
    }
  }

  private static CaptureNode convertCallsToNode(VmTraceData data, Call call, int depth, long topLevelStart) {

    CaptureNode node = new CaptureNode();
    // ART stores timestamp in a compressed fashion: timestamps of ClockType.GLOBAL type are 32 bits relative to a startTime.
    // We need to reconstruct the full timestamp by adding each of them to startTime.
    long globalStartTime = call.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + data.getStartTimeUs();
    node.setStartGlobal(globalStartTime);
    long globalEndTime = call.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + data.getStartTimeUs();
    node.setEndGlobal(globalEndTime);

    // Timestamps of ClockType.THREAD are stored in a different way: the first event on the thread is considered as the base
    // and the subsequent events timestamps are stored in 32 bits relative to that base. We sum this timestamps to topLevelStart,
    // so the first entry timestamp (represented as 0) is aligned (in wall clock time) with the top-level call start timestamp.
    long threadStartTime = topLevelStart + call.getEntryTime(ClockType.THREAD, TimeUnit.MICROSECONDS);
    node.setStartThread(threadStartTime);
    long threadEndTime = topLevelStart + call.getExitTime(ClockType.THREAD, TimeUnit.MICROSECONDS);
    node.setEndThread(threadEndTime);

    MethodModel method = new MethodModel(data.getMethod(call.getMethodId()).methodName);
    method.setClassName(data.getMethod(call.getMethodId()).className);
    method.setSignature(data.getMethod(call.getMethodId()).signature);
    node.setData(method);

    node.setDepth(depth);
    for (Call callee : call.getCallees()) {
      node.addHNode(convertCallsToNode(data, callee, depth + 1, topLevelStart));
    }
    return node;
  }

  public Map<ThreadInfo, CaptureNode> getThreadsGraph() {
    return myNodes;
  }
}
