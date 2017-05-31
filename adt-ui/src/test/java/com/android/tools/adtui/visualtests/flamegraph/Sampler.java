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

package com.android.tools.adtui.visualtests.flamegraph;

import com.android.tools.adtui.model.DefaultHNode;
import com.android.tools.adtui.flamegraph.SampledMethodUsage;
import com.android.tools.adtui.model.HNode;

import java.util.HashMap;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

public class Sampler implements Runnable {

  volatile boolean running;

  HashMap<String, DefaultHNode<SampledMethodUsage>> furnace;

  private final static long SAMPLING_RESOLUTION = TimeUnit.MILLISECONDS.toMillis(1);

  public static final int MAX_VALUE = 10000;

  public Sampler() {
    this.running = false;
  }


  public void startSampling() {
    this.running = true;
    this.furnace = new HashMap();
    Thread t = new Thread(this);
    t.start();
  }

  public void stopSampling() {
    this.running = false;
    generateStartsAndEnds();
  }

  @Override
  public void run() {
    while (running) {
      try {
        Thread.sleep(SAMPLING_RESOLUTION);
        if (running) {
          this.sample();
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private void sample() {
    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    for (Thread sampledThread : threadSet) {
      StackTraceElement[] elements = sampledThread.getStackTrace();

      // Some threads (e.g: SignalDispatcher) have no elements.
      if (elements.length == 0) {
        continue;
      }

      // Retrieve the ongoing flame associate with this thread.
      DefaultHNode<SampledMethodUsage> flame = furnace.get(sampledThread.getName());
      if (flame == null) {
        flame = new DefaultHNode();
        SampledMethodUsage m = new SampledMethodUsage();
        flame.setData(m);
        furnace.put(sampledThread.getName(), flame);
      }

      // Make sure this stacktrace has nodes. Create them if needed.
      ensureNodesExist(elements, flame);

      // Increase invocation counter for all element in the tracktrace.
      int depth = elements.length - 1;
      while (depth >= 0) {
        flame = findChildWithMethod(elements[depth], flame);
        flame.getData().incInvocationCounter();
        depth--;
      }
    }
  }

  private boolean isSameMethod(HNode<SampledMethodUsage> currentNode, StackTraceElement element) {
    return currentNode.getData().getName().equals(element.getMethodName()) &&
           currentNode.getData().getNameSpace().equals(element.getClassName());
  }

  private void ensureNodesExist(StackTraceElement[] trace, DefaultHNode<SampledMethodUsage> root) {
    DefaultHNode<SampledMethodUsage> previous;
    DefaultHNode<SampledMethodUsage> current = root;

    int depth = trace.length - 1;
    while (depth >= 0) {
      StackTraceElement currentMethod = trace[depth];
      previous = current;
      current = findChildWithMethod(currentMethod, previous);
      if (current == null) {
        // Create a new node.
        current = new DefaultHNode<>();
        current.setDepth(trace.length - depth);
        SampledMethodUsage m = new SampledMethodUsage();
        m.setInvocationCount(0);
        m.setNamespace(currentMethod.getClassName());
        m.setName(currentMethod.getMethodName());
        current.setData(m);
        // Add new node to parent
        previous.addChild(current);
      }
      depth--;
    }
  }

  private DefaultHNode<SampledMethodUsage> findChildWithMethod(StackTraceElement stackTraceElement,
                                                               DefaultHNode<SampledMethodUsage> node) {
    for (DefaultHNode<SampledMethodUsage> n : node.getChildren()) {
      if (isSameMethod(n, stackTraceElement)) {
        return n;
      }
    }
    return null;
  }

  private void generateStartsAndEnds() {
    for (String threadName : furnace.keySet()) {
      DefaultHNode<SampledMethodUsage> root = furnace.get(threadName);
      // HChart nodes are integers. We represent percentage with two decimals * 100.
      root.setStart(0);
      root.setEnd(MAX_VALUE);
      root.getData().setInvocationCount(sumInvocationCountForTree(root));
      // Recursion
      generateChildsStartsAndEnds(root);
    }
  }

  private void generateChildsStartsAndEnds(DefaultHNode<SampledMethodUsage> node) {
    // Get total invocation count for this depth level.
    long childInvocationTotal = 0;
    for (HNode<SampledMethodUsage> n : node.getChildren()) {
      childInvocationTotal += n.getData().getInvocationCount();
    }

    long childWidth = node.getEnd() - node.getStart();

    // For each child, generate: start, end and the absolute percentage.
    long base = node.getStart();
    for (DefaultHNode<SampledMethodUsage> child : node.getChildren()) {
      child.setStart(base);
      child.setEnd(base +
                   (long)(childWidth * (child.getData().getInvocationCount()
                                        / (float)childInvocationTotal)));
      child.getData().setPercentage((child.getEnd() - child.getStart()) / (float)(MAX_VALUE));
      base = child.getEnd();
    }

    // Recurse for each child
    for (DefaultHNode<SampledMethodUsage> child : node.getChildren()) {
      generateChildsStartsAndEnds(child);
    }
  }

  private long sumInvocationCountForTree(DefaultHNode<SampledMethodUsage> root) {
    long invocationCount = 0;
    Stack<DefaultHNode<SampledMethodUsage>> stack = new Stack<>();
    stack.addAll(root.getChildren());
    while (!stack.isEmpty()) {
      DefaultHNode<SampledMethodUsage> n = stack.pop();
      invocationCount += n.getData().getInvocationCount();
      stack.addAll(n.getChildren());
    }
    return invocationCount;
  }

  public HashMap<String, DefaultHNode<SampledMethodUsage>> getData() {
    return furnace;
  }
}
