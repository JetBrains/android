/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.art;

import com.android.tools.perflib.vmtrace.*;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MethodModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ArtTraceHandler implements VmTraceHandler {
  private static final String KEY_ELAPSED_TIME_US = "elapsed-time-usec";
  private long myStartTimeUs;
  private long myElapsedTimeUs;

  /**
   * Map from thread ids to thread names.
   */
  private final Map<Integer, String> myThreads = new HashMap<>();

  /**
   * Map from method id to method model.
   */
  private final Map<Long, MethodModel> myMethods = new HashMap<>();

  /**
   * Map from thread id to per thread stack call constructor.
   */
  private final Map<Integer, CaptureNodeConstructor> myNodeConstructors = new HashMap<>();

  @Nullable
  private Map<CpuThreadInfo, CaptureNode> myThreadsGraph;

  @Override
  public void addThread(int id, String name) {
    myThreads.put(id, name);
  }

  @Override
  public void addMethod(long id, MethodInfo info) {
    myMethods.put(id, new MethodModel(info.methodName, info.className, info.signature));
  }

  @Override
  public void addMethodAction(int threadId, long methodId, TraceAction methodAction,
                              int threadTime, int globalTime) {
    // create thread info if it doesn't exist
    if (!myThreads.containsKey(threadId)) {
      myThreads.put(threadId, String.format("Thread id: %1$d", threadId));
    }

    // create method info if it doesn't exist
    if (!myMethods.containsKey(methodId)) {
      myMethods.put(methodId, new MethodModel("unknown", "unknown", "unknown"));
    }

    CaptureNodeConstructor constructor = myNodeConstructors.get(threadId);
    if (constructor == null) {
      MethodModel topLevelModel = createUniqueMethodForThread(threadId);
      constructor = new CaptureNodeConstructor(topLevelModel);
      myNodeConstructors.put(threadId, constructor);
    }
    constructor.addTraceAction(myMethods.get(methodId), methodAction, threadTime, globalTime);
  }

  private MethodModel createUniqueMethodForThread(int threadId) {
    long id = Long.MAX_VALUE - threadId;
    assert myMethods.get(id) == null :
      "Unexpected error while attempting to create a unique key - key already exists";
    MethodModel model = new MethodModel(myThreads.get(threadId));
    myMethods.put(id, model);
    return model;
  }

  public Map<CpuThreadInfo, CaptureNode> getThreadsGraph() {
    if (myThreadsGraph == null) {
      myThreadsGraph = createThreadsGraph();
    }
    return myThreadsGraph;
  }

  @NotNull
  private Map<CpuThreadInfo, CaptureNode> createThreadsGraph() {
    Map<CpuThreadInfo, CaptureNode> threadsGraph = new HashMap<>(myThreads.size());

    for (Map.Entry<Integer, String> entry : myThreads.entrySet()) {
      final int id = entry.getKey();
      final String name = entry.getValue();

      CaptureNodeConstructor constructor = myNodeConstructors.get(id);
      if (constructor == null) {
        continue;
      }

      CaptureNode topLevelCall = constructor.getTopLevel();
      assert topLevelCall != null;
      CpuThreadInfo info = new CpuThreadInfo(id, name);

      long topLevelGlobalStart = topLevelCall.getStartGlobal() + myStartTimeUs;
      adjustNodesTimeAndDepth(topLevelCall, topLevelGlobalStart, 0);

      threadsGraph.put(info, topLevelCall);
    }

    return threadsGraph;
  }

  /**
   * Adjusts global and thread time from relative to absolute time and the depth of nodes.
   */
  private void adjustNodesTimeAndDepth(CaptureNode node, long topLevelStart, int depth) {
    node.setStartGlobal(myStartTimeUs + node.getStartGlobal());
    node.setEndGlobal(myStartTimeUs + node.getEndGlobal());
    node.setDepth(depth);
    // Timestamps of ClockType.THREAD are stored in a different way: the first event on the thread is considered as the base
    // and the subsequent events timestamps are stored in 32 bits relative to that base. We sum this timestamps to topLevelStart,
    // so the first entry timestamp (represented as 0) is aligned (in wall clock time) with the top-level call start timestamp.
    node.setStartThread(topLevelStart + node.getStartThread());
    node.setEndThread(topLevelStart + node.getEndThread());

    for (CaptureNode callee : node.getChildren()) {
      adjustNodesTimeAndDepth(callee, topLevelStart, depth + 1);
    }
  }

  public long getElapsedTimeUs() {
    return myElapsedTimeUs;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  @Override
  public void setStartTimeUs(long startTimeUs) {
    myStartTimeUs = startTimeUs;
  }

  @Override
  public void setProperty(String key, String value) {
    if (key.equals(KEY_ELAPSED_TIME_US)) {
      myElapsedTimeUs = Long.parseLong(value);
    }
  }

  @Override
  public void setVersion(int version) {
    // We don't need this information
  }
}
