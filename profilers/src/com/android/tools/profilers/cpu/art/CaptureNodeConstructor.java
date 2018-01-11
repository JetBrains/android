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

import com.android.tools.perflib.vmtrace.TraceAction;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link CaptureNodeConstructor} helps in constructing per thread call stacks from a sequence of
 * trace events (method entry/exit events).
 */
class CaptureNodeConstructor {
  /**
   * Method model corresponding to the top level node under which all calls are nested.
   */
  private final CaptureNodeModel myTopLevelNodeModel;

  /**
   * List of nodes currently assumed to be at stack depth 0 (called from the top level)
   */
  private final List<CaptureNode> myTopLevelNodes = new ArrayList<>();

  /**
   * The current node, i.e it is updated when we enter or exit a method.
   */
  @Nullable
  private CaptureNode myCurrentNode;

  /**
   * The single top level node under which the entire constructed call stack nests.
   */
  private CaptureNode myTopLevelNode;

  CaptureNodeConstructor(CaptureNodeModel captureNodeModel) {
    myTopLevelNodeModel = captureNodeModel;
  }

  void addTraceAction(CaptureNodeModel captureNodeModel, TraceAction action, int threadTime, int globalTime) {
    if (action == TraceAction.METHOD_ENTER) {
      enterMethod(captureNodeModel, threadTime, globalTime);
    } else {
      exitMethod(captureNodeModel, threadTime, globalTime);
    }
  }

  private void enterMethod(CaptureNodeModel captureNodeModel, int threadTime, int globalTime) {
    CaptureNode node = new CaptureNode(captureNodeModel);
    node.setStartGlobal(globalTime);
    node.setStartThread(threadTime);

    if (myCurrentNode == null) {
      myTopLevelNodes.add(node);
    } else {
      myCurrentNode.addChild(node);
    }

    myCurrentNode = node;
  }

  private void exitMethod(CaptureNodeModel captureNodeModel, long threadTime, long globalTime) {
    if (myCurrentNode != null) {
      if (myCurrentNode.getData() != captureNodeModel) {
        String msg = String
          .format("Error during call stack reconstruction. Attempt to exit from method %s while in method %s",
                  myCurrentNode.getData().getId(), captureNodeModel.getId());
        throw new RuntimeException(msg);
      }

      myCurrentNode.setEndGlobal(globalTime);
      myCurrentNode.setEndThread(threadTime);
      myCurrentNode = myCurrentNode.getParent();
    } else {
      // We are exiting out of a method that was entered into before tracing was started.
      // In such a case, create this method
      CaptureNode node = new CaptureNode(captureNodeModel);
      // All the previous nodes at the top level are now assumed to have been called from
      // this method. So mark this method as having called all of those methods, and reset
      // the top level to only include this method
      for (CaptureNode topLevel : myTopLevelNodes) {
        node.addChild(topLevel);
      }
      myTopLevelNodes.clear();
      myTopLevelNodes.add(node);

      node.setEndGlobal(globalTime);
      node.setEndThread(threadTime);

      // We don't know this method's entry times, so we try to guess:
      // If it has at least 1 child, then we know it must've been at least before that child's
      // start time. If there are no children, then we just assume that it was just before its
      // exit time.
      long entryThreadTime = threadTime - 1;
      long entryGlobalTime = globalTime - 1;

      if (node.getChildCount() > 0) {
        CaptureNode first = node.getFirstChild();
        assert first != null;
        entryThreadTime = Math.max(first.getStartThread() - 1, 0);
        entryGlobalTime = Math.max(first.getStartGlobal() - 1, 0);
      }
      node.setStartGlobal(entryGlobalTime);
      node.setStartThread(entryThreadTime);
    }
  }

  /**
   * Generates a trace action equivalent to exiting from the given method
   * @param captureNodeModel model of the method from which we are exiting
   * @param entryThreadTime method's thread entry time
   * @param entryGlobalTime method's global entry time
   * @param children from the method that we are exiting
   */
  private void exitMethod(CaptureNodeModel captureNodeModel, long entryThreadTime, long entryGlobalTime,
                          @Nullable List<CaptureNode> children) {
    long lastExitThreadTime;
    long lastExitGlobalTime;

    if (children == null || children.isEmpty()) {
      // if the call doesn't have any children, we assume that it just ran for 1us.
      lastExitThreadTime = entryThreadTime + 1;
      lastExitGlobalTime = entryGlobalTime + 1;
    } else {
      // if it did call other methods, we assume that this call exited 1us after
      // its last child exited
      CaptureNode last = children.get(children.size() - 1);
      lastExitThreadTime = last.getEndThread() + 1;
      lastExitGlobalTime = last.getEndGlobal() + 1;
    }

    exitMethod(captureNodeModel, lastExitThreadTime, lastExitGlobalTime);
  }

  private void fixUpCallStacks() {
    if (myTopLevelNode != null) {
      return;
    }

    // If there are any methods still on the call stack, then the trace doesn't have
    // exit trace action for them, so clean those up
    //noinspection WhileLoopSpinsOnField
    while (myCurrentNode != null) {
      exitMethod(myCurrentNode.getData(), myCurrentNode.getStartThread(),
                 myCurrentNode.getStartGlobal(), myCurrentNode.getChildren());
    }

    // Now that we have parsed the entire call stack, let us move all of it under a single
    // top level call.
    exitMethod(myTopLevelNodeModel, 0, 0, myTopLevelNodes);

    // Build calls from their respective builders
    // Now that we've added the top level call, there should be only 1 top level call
    assert myTopLevelNodes.size() == 1;
    myTopLevelNode = myTopLevelNodes.get(0);
  }

  public CaptureNode getTopLevel() {
    fixUpCallStacks();
    return myTopLevelNode;
  }
}
