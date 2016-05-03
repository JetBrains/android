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

package com.android.tools.adtui.visual.threadgraph;

import com.android.tools.adtui.hchart.HNode;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Sampler implements Runnable {

  long lastSampleTime;
  long currSampleTime;

  volatile boolean running;

  private HashMap<String, HNode<Method>> forest;

  private final static long SAMPLING_RESOLUTION = TimeUnit.MILLISECONDS.toMillis(1);

  public Sampler() {
    this.running = false;
  }

  // Feeding high values to chartlib is triggering integer overflows: Using micro precision
  // instead of nano.
  // TODO: Fix this issue and revert to sampling with nano precision.
  private long currentTimeMicroSec() {
    return System.nanoTime() / 1000;
  }

  public void startSampling() {
    this.running = true;
    this.currSampleTime = currentTimeMicroSec();
    this.forest = new HashMap<>();
    Thread t = new Thread(this);
    t.start();
  }

  public void stopSampling() {
    this.running = false;
    endOngoingCalls();
  }

  @Override
  public void run() {
    while (this.running) {
      try {
        java.lang.Thread.sleep(SAMPLING_RESOLUTION);
        this.sample();
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private void sample() {
    lastSampleTime = currSampleTime;
    currSampleTime = currentTimeMicroSec();
    Set<java.lang.Thread> threadSet = java.lang.Thread.getAllStackTraces().keySet();
    for (java.lang.Thread sampledThread : threadSet) {

      StackTraceElement[] elements = sampledThread.getStackTrace();

      // Some threads (e.g: SignalDispatcher) have no elements.
      if (elements.length == 0) {
        continue;
      }

      // Retrieve the ongoing tree associate with this thread.
      HNode<Method> tree = forest.get(sampledThread.getName());
      if (tree == null) {
        tree = new HNode();
        tree.setStart(getLastMidTime());
        Method rootMethod = new Method();
        rootMethod.setName("rootMethod");
        rootMethod.setNamespace("root.package.foo.bar");
        tree.setData(rootMethod);
        forest.put(sampledThread.getName(), tree);
      }

      // Compare last captured stack with current stack. Stop as soon as they diverge.
      int depth = elements.length - 1;
      HNode<Method> previousNode = tree;
      HNode<Method> currentNode = previousNode.getLastChild();
      while (currentNode != null && depth >= 0 && isSameMethod(currentNode,
                                                               elements[depth])) {
        depth--;
        previousNode = currentNode;
        currentNode = currentNode.getLastChild();
      }

      // We found the point where the stacks diverge. We need to:
      // 1. Mark previous calls are ended via timestamps.
      // 2. Insert all new calls which are currently ongoing.

      //1. Mark all previous calls as ended.
      HNode endedCall = currentNode;
      while (endedCall != null) {
        endedCall.setEnd(getLastMidTime());
        endedCall = endedCall.getLastChild();
      }

      //2. Those are new calls on the stack: Add them to the tree.
      while (depth != -1) {
        StackTraceElement trace = elements[depth];

        // New node data is a Method.
        Method m = new Method();
        m.setNamespace(trace.getClassName());
        m.setName(trace.getMethodName());

        HNode<Method> newNode = new HNode<>();
        newNode.setStart(getLastMidTime());
        newNode.setData(m);
        newNode.setDepth(elements.length - depth - 1);
        previousNode.addHNode(newNode);

        previousNode = newNode;
        depth--;
      }
    }
  }


  long getLastMidTime() {
    long t = lastSampleTime + (currSampleTime - lastSampleTime) / 2;
    return t;
  }

  private boolean isSameMethod(HNode<Method> currentNode, StackTraceElement element) {
    return currentNode.getData().getName().equals(element.getMethodName()) &&
           currentNode.getData().getNameSpace().equals(element.getClassName());
  }

  private void endOngoingCalls() {
    lastSampleTime = currSampleTime;
    currSampleTime = currentTimeMicroSec();
    for (HNode n : forest.values()) {
      HNode cursor = n;
      while (cursor != null) {
        cursor.setEnd(getLastMidTime());
        cursor = cursor.getLastChild();
      }
    }
  }

  public HashMap<String, HNode<Method>> getData() {
    return forest;
  }
}
