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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterAccumulator;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaptureNode implements HNode<CaptureNode> {
  /**
   * Start time with GLOBAL clock.
   */
  private long myStartGlobal;

  /**
   * End time with GLOBAL clock.
   */
  private long myEndGlobal;

  /**
   * Start time with THREAD clock.
   */
  private long myStartThread;

  /**
   * End time with THREAD clock.
   */
  private long myEndThread;

  @NotNull
  protected final List<CaptureNode> myChildren;

  @NotNull
  private ClockType myClockType;

  /**
   * The parent of its child is set to it when it is added {@link #addChild(CaptureNode)}
   */
  private CaptureNode myParent;

  /**
   * see {@link FilterType}.
   */
  @NotNull
  private FilterType myFilterType;

  /**
   * The shortest distance from the root.
   */
  private int myDepth;

  @NotNull
  private final CaptureNodeModel myData;

  /**
   * Aspect model for the node {@link Aspect}. Only root nodes provide aspect changes so it is lazily initialized to avoid the overhead of
   * its instantiation.
   */
  @Nullable
  private AspectModel<Aspect> myAspectModel = null;

  public CaptureNode(@NotNull CaptureNodeModel model) {
    myChildren = new ArrayList<>();
    myClockType = ClockType.GLOBAL;
    myFilterType = FilterType.MATCH;
    myDepth = 0;
    myData = model;
  }

  public void addChild(CaptureNode node) {
    myChildren.add(node);
    node.myParent = this;
  }

  @NotNull
  public List<CaptureNode> getChildren() {
    return myChildren;
  }

  @NotNull
  public CaptureNodeModel getData() {
    return myData;
  }

  @Override
  public int getChildCount() {
    return myChildren.size();
  }

  @NotNull
  @Override
  public CaptureNode getChildAt(int index) {
    return myChildren.get(index);
  }

  @Nullable
  @Override
  public CaptureNode getParent() {
    return myParent;
  }

  /**
   * @return root node of this node. If this node doesn't have a parent, return this node.
   */
  @NotNull
  public CaptureNode findRootNode() {
    CaptureNode rootNode = this;
    while (rootNode.getParent() != null) {
      rootNode = rootNode.getParent();
    }
    return rootNode;
  }

  @Override
  public long getStart() {
    return myClockType == ClockType.THREAD ? myStartThread : myStartGlobal;
  }

  @Override
  public long getEnd() {
    return myClockType == ClockType.THREAD ? myEndThread : myEndGlobal;
  }

  @Override
  public int getDepth() {
    return myDepth;
  }

  public void setStartGlobal(long startGlobal) {
    myStartGlobal = startGlobal;
  }

  public long getStartGlobal() {
    return myStartGlobal;
  }

  public void setEndGlobal(long endGlobal) {
    myEndGlobal = endGlobal;
  }

  public long getEndGlobal() {
    return myEndGlobal;
  }

  public void setStartThread(long startThread) {
    myStartThread = startThread;
  }

  public long getStartThread() {
    return myStartThread;
  }

  public void setEndThread(long endThread) {
    myEndThread = endThread;
  }

  public long getEndThread() {
    return myEndThread;
  }

  public void setClockType(@NotNull ClockType clockType) {
    myClockType = clockType;
  }

  @NotNull
  public AspectModel<Aspect> getAspectModel() {
    if (myAspectModel == null) {
      myAspectModel = new AspectModel<>();
    }
    return myAspectModel;
  }

  /**
   * Returns the proportion of time the method was using CPU relative to the total (wall-clock) time that passed.
   */
  public double threadGlobalRatio() {
    long durationThread = myEndThread - myStartThread;
    long durationGlobal = myEndGlobal - myStartGlobal;
    return (double)durationThread / durationGlobal;
  }

  @NotNull
  public ClockType getClockType() {
    return myClockType;
  }

  public void setDepth(int depth) {
    myDepth = depth;
  }

  /**
   * Iterate through all descendants of this node, apply a filter and then find the top k nodes by the given comparator.
   *
   * @param k          number of results
   * @param filter     keep only nodes that satisfies this filter
   * @param comparator to compare nodes by
   * @return up to top k nodes from all descendants, in descending order
   */
  @NotNull
  public List<CaptureNode> getTopKNodes(int k, @NotNull Predicate<CaptureNode> filter, @NotNull Comparator<CaptureNode> comparator) {
    // Put all matched nodes in a priority queue capped at size n, so the queue always contain the n longest running ones.
    PriorityQueue<CaptureNode> candidates = new PriorityQueue<>(k + 1, comparator);
    getDescendantsStream().filter(filter).forEach(node -> {
      candidates.offer(node);
      if (candidates.size() > k) {
        candidates.poll();
      }
    });
    List<CaptureNode> result = new ArrayList<>(candidates);
    Collections.sort(result, comparator.reversed());
    return result;
  }

  /**
   * @return all descendants in pre-order (i.e. node, left, right) as a stream.
   */
  public Stream<CaptureNode> getDescendantsStream() {
    return Stream.concat(Stream.of(this), getChildren().stream().flatMap(CaptureNode::getDescendantsStream));
  }

  /**
   * Apply a filter to this node and its children.
   *
   * @param filter filter to apply. An empty matches all nodes.
   * @return filter result, e.g. number of matches.
   */
  @NotNull
  public FilterResult applyFilter(@NotNull Filter filter) {
    FilterAccumulator accumulator = new FilterAccumulator(!filter.isEmpty());
    computeFilter(filter, false, accumulator);
    if (myAspectModel != null) {
      myAspectModel.changed(Aspect.FILTER_APPLIED);
    }
    return accumulator.toFilterResult();
  }


  /**
   * Recursively applies filter to this node and its children.
   */
  private void computeFilter(@NotNull Filter filter, boolean matches, @NotNull FilterAccumulator accumulator) {
    boolean nodeExactMatch = filter.matches(getData().getFullName());
    matches = matches || nodeExactMatch;
    if (nodeExactMatch) {
      accumulator.increaseMatchCount();
    }
    accumulator.increaseTotalCount();

    boolean allChildrenUnmatch = true;
    for (CaptureNode child : getChildren()) {
      child.computeFilter(filter, matches, accumulator);
      if (!child.isUnmatched()) {
        allChildrenUnmatch = false;
      }
    }

    if (!matches && allChildrenUnmatch) {
      setFilterType(FilterType.UNMATCH);
    }
    else if (nodeExactMatch && !filter.isEmpty()) {
      setFilterType(FilterType.EXACT_MATCH);
    }
    else {
      setFilterType(FilterType.MATCH);
    }
  }

  @NotNull
  public FilterType getFilterType() {
    return myFilterType;
  }

  public void setFilterType(@NotNull FilterType type) {
    myFilterType = type;
  }

  public boolean isUnmatched() {
    return getFilterType() == FilterType.UNMATCH;
  }

  public enum FilterType {
    /**
     * This {@link CaptureNode} matches to the filter.
     */
    EXACT_MATCH,

    /**
     * Either one of its ancestor is a {@link #EXACT_MATCH} or one of its descendant.
     */
    MATCH,

    /**
     * Neither this node matches to the filter nor one of its ancestor nor one of its descendant.
     */
    UNMATCH,
  }
  public enum Aspect {
    /**
     * Fired when a {@link Filter} is applied to this node.
     */
    FILTER_APPLIED
  }
}
