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
import com.android.tools.perflib.vmtrace.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CpuTraceArt {

  /**
   * Tree representation of ART trace (generated from perflib tree).
   * Keys are thread ids and values are their respective {@link HNode}
   */
  Map<ThreadInfo, HNode<MethodModel>> myNodes;

  /**
   * Tree representation of ART trace (generated from raw trace).
   */
  private VmTraceData myData;


  public void parse(File traceFile) throws IOException {
    // Parse raw myData into perflib representation.
    VmTraceParser parser = new VmTraceParser(traceFile);
    parser.parse();
    myData = parser.getTraceData();
    myNodes = new HashMap<>();

    // Convert perflib tree to HNode tree.
    for (ThreadInfo threadInfo : myData.getThreads()) {
      if (threadInfo.getTopLevelCall() == null) {
        continue;
      }

      // ART stores the name of the thread in the first node. We need to skip it.
      Call firstCall = threadInfo.getTopLevelCall();
      HNode<MethodModel> root = new HNode<>();

      // ART stores timestamp in a compressed fashion: All timestamp are 32 bits relative to a startTime.
      // We need to reconstruct the full timestamp by adding each of them to startTime.
      root.setStart(firstCall.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs());
      root.setEnd((firstCall.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      myNodes.put(threadInfo, root);
      convertCallsToNode(Collections.singletonList(firstCall), root, 0);
    }
  }

  // Convert perflib |calls| to HNodes and add them to |root|.
  private void convertCallsToNode(List<Call> calls, HNode<MethodModel> root, int depth) {
    for (Call c : calls) {
      HNode<MethodModel> node = new HNode<>();
      node.setStart((c.getEntryTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      node.setEnd((c.getExitTime(ClockType.GLOBAL, TimeUnit.MICROSECONDS) + myData.getStartTimeUs()));
      node.setDepth(depth);

      MethodModel method = new MethodModel();
      method.setName(myData.getMethod(c.getMethodId()).methodName);
      method.setNamespace(myData.getMethod(c.getMethodId()).className);
      node.setData(method);

      root.addHNode(node);

      // Recursion
      convertCallsToNode(c.getCallees(), node, depth + 1);
    }
  }

  /**
   * Write the bytes to a file and parses it by calling {@link #parse} on that file.
   */
  public void trace(byte[] traceBytes) {
    // TODO: Store the data in the datastore
    FileOutputStream fileOutput;
    try {
      // TODO: Write the files in a non-blocking way. Change it to a bazel-friendly file manipulation.
      String fileName = createTraceFileName(new File("."));
      fileOutput = new FileOutputStream(fileName);
      fileOutput.write(traceBytes);
      fileOutput.close();
      parse(new File(fileName));
      // TODO: delete file

    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Create a name for new CPU trace files that's unique during the lifetime of current process.
   */
  private static String createTraceFileName(File directory) throws IOException {
    return String.format("%s%st%d_%d.cpu_trace",
                         directory.getCanonicalPath(),
                         File.separator,
                         Thread.currentThread().getId(),
                         System.nanoTime());
  }

  public Map<ThreadInfo, HNode<MethodModel>> getThreadsGraph() {
    return myNodes;
  }
}
