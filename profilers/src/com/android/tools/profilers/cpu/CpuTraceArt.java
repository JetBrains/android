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
  Map<ThreadInfo, HNode<MethodModel>> myNodes;

  public void parse(VmTraceData data) throws IOException {
    myNodes = new HashMap<>();

    // Convert perflib tree to HNode tree.
    for (ThreadInfo threadInfo : data.getThreads()) {
      if (threadInfo.getTopLevelCall() == null) {
        continue;
      }
      myNodes.put(threadInfo, convertCallsToNode(data, threadInfo.getTopLevelCall(), 0));
    }
  }

  private HNode<MethodModel> convertCallsToNode(VmTraceData data, Call call, int depth) {

    HNode<MethodModel> node = new HNode<>();
    // ART stores timestamp in a compressed fashion: All timestamp are 32 bits relative to a startTime.
    // We need to reconstruct the full timestamp by adding each of them to startTime.
    node.setStart((call.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + data.getStartTimeUs()));
    node.setEnd((call.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + data.getStartTimeUs()));
    node.setDepth(depth);

    MethodModel method = new MethodModel();
    method.setName(data.getMethod(call.getMethodId()).methodName);
    method.setNamespace(data.getMethod(call.getMethodId()).className);
    node.setData(method);

    for (Call callee : call.getCallees()) {
      node.addHNode(convertCallsToNode(data, callee, depth + 1));
    }
    return node;
  }

  public Map<ThreadInfo, HNode<MethodModel>> getThreadsGraph() {
    return myNodes;
  }
}
