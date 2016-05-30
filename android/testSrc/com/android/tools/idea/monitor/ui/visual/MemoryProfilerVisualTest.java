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

package com.android.tools.idea.monitor.ui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.visual.VisualTest;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.memory.model.MemoryInfoTreeNode;
import com.android.tools.idea.monitor.ui.memory.view.MemoryDetailSegment;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

public class MemoryProfilerVisualTest extends VisualTest {

  private static final String MEMORY_PROFILER_NAME = "Memory Profiler";

  private SeriesDataStore mDataStore;

  private MemorySegment mSegment;

  private MemoryDetailSegment mDetailSegment;

  private MemoryInfoTreeNode mRoot;

  private Thread mUpdateDataThread;

  @Override
  protected void initialize() {
    mDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (mDataStore != null) {
      mDataStore.reset();
    }

    if (mUpdateDataThread != null) {
      mUpdateDataThread.interrupt();
    }

    super.reset();
  }

  @Override
  public String getName() {
    return MEMORY_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    long startTimeMs = System.currentTimeMillis();
    Range xRange = new Range();
    AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(xRange, startTimeMs);
    mSegment = new MemorySegment(xRange, mDataStore);
    mRoot = new MemoryInfoTreeNode("Root");
    mDetailSegment = new MemoryDetailSegment(xRange, mRoot);
    List<Animatable> animatables = new ArrayList<>();
    animatables.add(animatedTimeRange);
    animatables.add(xRange);
    mSegment.createComponentsList(animatables);
    mDetailSegment.createComponentsList(animatables);

    // Simulate allocation data with stack frames.
    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            updateTree(mRoot);
            Thread.sleep(10);
          }
        } catch (InterruptedException e) {
        }
      }
    };
    mUpdateDataThread.start();

    return animatables;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 1;
    constraints.weightx = 1;
    constraints.gridy = 0;
    mSegment.initializeComponents();
    mSegment.toggleView(true);
    panel.add(mSegment, constraints);

    constraints.gridy = 1;
    mDetailSegment.initializeComponents();
    panel.add(mDetailSegment, constraints);
  }

  /**
   * Constructs/updates a tree structure based on all available stack traces. For each stack frame that appears, we increment a counter
   * on each node along the stack frame's path to document its occurrence frequency. The information is used by the MemoryDetailSegment
   * class to render the delta visuals behind the count/size columns.
   *
   * TODO consider replacing with fake memory allocation data.
   */
  private void updateTree(MemoryInfoTreeNode root) {
    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    boolean hasNewChildren = false;
    for (Thread thread : threadSet) {
      StackTraceElement[] traces = thread.getStackTrace();

      if (traces.length == 0) {
        // Discard zero-length traces.
        continue;
      }

      hasNewChildren |= constructAndIncrementNodes(root, traces, traces.length - 1);

      // Increment the root node to document the total number of stack frames over time.
      root.incrementCount();
    }

    if (hasNewChildren) {
      mDetailSegment.refreshNode(root);
    }
  }

  private boolean constructAndIncrementNodes(MemoryInfoTreeNode parent, StackTraceElement[] traces, int depth) {
    if (depth < 0) {
      // Reached top of stack - early return.
      return false;
    }

    StackTraceElement trace = traces[depth];
    String nameSpace = trace.getClassName();

    // Skip non-user code namespace to avoid deep stack frames.
    if (nameSpace.startsWith("java.") ||
        nameSpace.startsWith("sun.") ||
        nameSpace.startsWith("javax.") ||
        nameSpace.startsWith("apple.") ||
        nameSpace.startsWith("com.apple.")) {
      return constructAndIncrementNodes(parent, traces, --depth);
    }
    
    // Attempt to find an existing matching child node - create one if it does not exist.
    MemoryInfoTreeNode matchedChild = null;
    boolean isNewNode = false;
    String fullName = nameSpace + "." + trace.getMethodName();
    Enumeration children = parent.children();
    while (children.hasMoreElements()) {
      MemoryInfoTreeNode child = (MemoryInfoTreeNode)children.nextElement();
      if (child != null && child.getName().equals(fullName)) {
        matchedChild = child;
        break;
      }
    }

    if (matchedChild == null) {
      matchedChild = new MemoryInfoTreeNode(fullName);
      isNewNode = true;
    }

    // Increase the occurrence count of this node.
    matchedChild.incrementCount();
    boolean childrenChanged = constructAndIncrementNodes(matchedChild, traces, --depth);

    if (isNewNode) {
      // If the current node is new, simply insert it and propagates back up to the parent to refresh.
      mDetailSegment.insertNode(parent, matchedChild);
    } else if (childrenChanged) {
      // If the node is not new and its children has changed, refresh itself and restore the previous expansion state.
      boolean expanded = mDetailSegment.getExpandState(matchedChild);
      mDetailSegment.refreshNode(matchedChild);
      if (expanded) {
        mDetailSegment.setExpandState(matchedChild, expanded);
      }
    }

    return isNewNode;
  }
}
