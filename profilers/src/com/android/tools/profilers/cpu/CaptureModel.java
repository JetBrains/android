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
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Manages states of the selected capture, such as current select thread, capture details (i.e top down tree, bottom up true, chart).
 * When a state changes, this class lets all view know about the changes they're interested in.
 */
class CaptureModel {
  /**
   * A negligible number. It is used for comparision.
   */
  private static final double EPSILON = 1e-5;

  /**
   * Negative number used when no thread is selected.
   */
  public static final int NO_THREAD = -1;

  @NotNull
  private final CpuProfilerStage myStage;

  @Nullable
  private CpuCapture myCapture;

  private int myThread;

  @NotNull
  private ClockType myClockType = ClockType.GLOBAL;

  /**
   * A filter that is applied to the current {@link CaptureNode}.
   */
  @Nullable
  private Pattern myFilter;

  @Nullable
  private Details myDetails;

  /**
   * Reference to a selection range converted to ClockType.THREAD.
   */
  private final Range myCaptureConvertedRange;

  CaptureModel(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myCaptureConvertedRange = new Range();
    myThread = NO_THREAD;

    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    selection.addDependency(myStage.getAspect()).onChange(Range.Aspect.RANGE, this::updateCaptureConvertedRange);
    myCaptureConvertedRange.addDependency(myStage.getAspect()).onChange(Range.Aspect.RANGE, this::updateSelectionRange);
  }

  void setCapture(@Nullable CpuCapture capture) {
    if (myCapture == capture) {
      return;
    }
    myCapture = capture;
    if (myCapture != null) {
      // If a thread was already selected, keep the selection. Otherwise select the capture main thread.
      setThread(myThread != NO_THREAD ? myThread : capture.getMainThreadId());
      myCapture.updateClockType(myClockType);
    }
    else {
      setThread(NO_THREAD);
    }
    rebuildDetails();
    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE_SELECTION);
  }

  @Nullable
  CpuCapture getCapture() {
    return myCapture;
  }

  void setThread(int thread) {
    if (myThread == thread) {
      return;
    }
    myThread = thread;
    rebuildDetails();
    myStage.getAspect().changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  int getThread() {
    return myThread;
  }

  void setClockType(@NotNull ClockType type) {
    if (myClockType == type) {
      return;
    }
    myClockType = type;
    if (myCapture != null) {
      myCapture.updateClockType(myClockType);
    }
    rebuildDetails();
    updateCaptureConvertedRange();
    myStage.getAspect().changed(CpuProfilerAspect.CLOCK_TYPE);
  }

  @NotNull
  ClockType getClockType() {
    return myClockType;
  }

  void setFilter(@Nullable Pattern filter) {
    if (Objects.equals(filter, myFilter)) {
      return;
    }
    myFilter = filter;
    rebuildDetails();
  }

  void setDetails(@Nullable Details.Type type) {
    if (type != null && myDetails != null && type == myDetails.getType()) {
      return;
    }
    buildDetails(type);
  }

  @Nullable
  Details getDetails() {
    return myDetails;
  }

  private void rebuildDetails() {
    if (myCapture == null) {
      buildDetails(null);
    }
    else {
      buildDetails(myDetails == null ? Details.Type.CALL_CHART : myDetails.getType());
    }
  }

  private void buildDetails(@Nullable Details.Type type) {
    updateCaptureConvertedRange();

    if (type != null) {
      CaptureNode node = getNode();
      if (node != null) {
        applyFilter(node, false);
      }
      myDetails = type.build(myCaptureConvertedRange, node);
    }
    else {
      myDetails = null;
    }

    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE_DETAILS);
  }

  @Nullable
  private CaptureNode getNode() {
    return myCapture != null ? myCapture.getCaptureNode(myThread) : null;
  }

  /**
   * Applies the current filter {@link #myFilter} to the {@param node}.
   *
   * @param node    - a node to apply the current filter
   * @param matches - whether there is a match to the filter in one of its ancestors.
   */
  private void applyFilter(@NotNull CaptureNode node, boolean matches) {
    boolean nodeExactMatch = node.matchesToFilter(myFilter);
    matches = matches || nodeExactMatch;
    boolean allChildrenUnmatch = true;
    for (CaptureNode child : node.getChildren()) {
      applyFilter(child, matches);
      if (!child.isUnmatched()) {
        allChildrenUnmatch = false;
      }
    }

    if (!matches && allChildrenUnmatch) {
      node.setFilterType(CaptureNode.FilterType.UNMATCH);
    }
    else if (nodeExactMatch && myFilter != null) {
      node.setFilterType(CaptureNode.FilterType.EXACT_MATCH);
    }
    else {
      node.setFilterType(CaptureNode.FilterType.MATCH);
    }
  }

  /**
   * When using ClockType.THREAD, we need to scale the selection to actually select a relevant range in the capture.
   * That happens because selection is based on wall-clock time, which is usually way greater than thread time.
   * As the two types of clock are synced at start time, making a selection starting at a time
   * greater than (start + thread time length) will result in no feedback for the user, which is wrong.
   * Therefore, we scale the selection so we can provide relevant thread time data as the user changes selection.
   */
  private void updateCaptureConvertedRange() {
    // TODO: improve performance of select range conversion.
    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    ClockType clockType = getClockType();
    CpuCapture capture = getCapture();
    CaptureNode node;
    if (clockType == ClockType.GLOBAL || capture == null || (node = capture.getCaptureNode(getThread())) == null) {
      setConvertedRange(selection.getMin(), selection.getMax());
      return;
    }

    double convertedMin = node.getStartThread() + node.threadGlobalRatio() * (selection.getMin() - node.getStartGlobal());
    double convertedMax = convertedMin + node.threadGlobalRatio() * selection.getLength();

    setConvertedRange(convertedMin, convertedMax);
  }

  /**
   * Updates the selection range based on the converted range in case THREAD clock is being used.
   */
  private void updateSelectionRange() {
    // TODO: improve performance of range conversion.
    ClockType clockType = getClockType();
    CpuCapture capture = getCapture();
    CaptureNode node;
    if (clockType == ClockType.GLOBAL || capture == null || (node = capture.getCaptureNode(getThread())) == null) {
      setSelectionRange(myCaptureConvertedRange.getMin(), myCaptureConvertedRange.getMax());
      return;
    }
    double threadToGlobal = 1 / node.threadGlobalRatio();
    double convertedMin = node.getStartGlobal() + threadToGlobal * (myCaptureConvertedRange.getMin() - node.getStartThread());
    double convertedMax = convertedMin + threadToGlobal * myCaptureConvertedRange.getLength();
    setSelectionRange(convertedMin, convertedMax);
  }

  /**
   * Converted range updates selection range and vice-versa.
   *
   * If it's almost identical to the selection range, don't update it.
   * This prevents from updating each other in a loop.
   */
  private void setSelectionRange(double min, double max) {
    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    if (Math.abs(selection.getMin() - min) > EPSILON || Math.abs(selection.getMax() - max) > EPSILON) {
      selection.set(min, max);
    }
  }

  /**
   * Converted range updates selection range and vice-versa.
   *
   * If it's almost identical to the range, don't update it.
   * This prevents from updating each other in a loop.
   */
  private void setConvertedRange(double min, double max) {
    if (Math.abs(myCaptureConvertedRange.getMin() - min) > EPSILON || Math.abs(myCaptureConvertedRange.getMax() - max) > EPSILON) {
      myCaptureConvertedRange.set(min, max);
    }
  }

  public interface Details {
    enum Type {
      TOP_DOWN(TopDown::new),
      BOTTOM_UP(BottomUp::new),
      CALL_CHART(CallChart::new),
      FLAME_CHART(FlameChart::new);

      @NotNull
      private final BiFunction<Range, CaptureNode, Details> myBuilder;

      Type(@NotNull BiFunction<Range, CaptureNode, Details> builder) {
        myBuilder = builder;
      }

      public Details build(Range range, CaptureNode node) {
        return myBuilder.apply(range, node);
      }
    }

    Type getType();
  }

  public static class TopDown implements Details {
    @Nullable private TopDownTreeModel myModel;

    public TopDown(@NotNull Range range, @Nullable CaptureNode node) {
      myModel = node == null ? null : new TopDownTreeModel(range, new TopDownNode(node));
    }

    @Nullable
    public TopDownTreeModel getModel() {
      return myModel;
    }

    @Override
    public Type getType() {
      return Type.TOP_DOWN;
    }
  }

  public static class BottomUp implements Details {
    @Nullable private BottomUpTreeModel myModel;

    public BottomUp(@NotNull Range range, @Nullable CaptureNode node) {
      myModel = node == null ? null : new BottomUpTreeModel(range, new BottomUpNode(node));
    }

    @Nullable
    public BottomUpTreeModel getModel() {
      return myModel;
    }

    @Override
    public Type getType() {
      return Type.BOTTOM_UP;
    }
  }

  public static class CallChart implements Details {
    @NotNull private final Range myRange;
    @Nullable private CaptureNode myNode;

    public CallChart(@NotNull Range range, @Nullable CaptureNode node) {
      myRange = range;
      myNode = node;
    }

    @NotNull
    public Range getRange() {
      return myRange;
    }

    @Nullable
    public CaptureNode getNode() {
      return myNode;
    }

    @Override
    public Type getType() {
      return Type.CALL_CHART;
    }
  }

  public static class FlameChart implements Details {
    public enum Aspect {
      /**
       * When the root changes.
       */
      NODE
    }

    @NotNull private final Range myFlameRange;
    @Nullable private CaptureNode myFlameNode;
    @Nullable private final TopDownNode myTopDownNode;

    @NotNull private final Range mySelectionRange;
    @NotNull private final AspectModel<Aspect> myAspectModel;

    public FlameChart(@NotNull Range selectionRange, @Nullable CaptureNode captureNode) {
      mySelectionRange = selectionRange;
      myFlameRange = new Range();
      myAspectModel = new AspectModel<>();

      if (captureNode == null) {
        myFlameNode = null;
        myTopDownNode = null;
        return;
      }
      myTopDownNode = new TopDownNode(captureNode);

      selectionRange.addDependency(myAspectModel).onChange(Range.Aspect.RANGE, this::selectionRangeChanged);
      selectionRangeChanged();
    }

    private void selectionRangeChanged() {
      myTopDownNode.update(mySelectionRange);
      if (myTopDownNode.getTotal() > 0) {
        double start = Math.max(myTopDownNode.getNodes().get(0).getStart(), mySelectionRange.getMin());
        myFlameNode = convertToFlameChart(myTopDownNode, start, 0);
      }
      else {
        myFlameNode = null;
      }

      myFlameRange.set(mySelectionRange);
      myAspectModel.changed(Aspect.NODE);
    }

    @NotNull
    public Range getRange() {
      return myFlameRange;
    }

    @Nullable
    public CaptureNode getNode() {
      return myFlameNode;
    }

    @NotNull
    public AspectModel<Aspect> getAspect() {
      return myAspectModel;
    }

    @Override
    public Type getType() {
      return Type.FLAME_CHART;
    }

    /**
     * Produces a flame chart that is similar to {@link CallChart}, but the identical methods with the same sequence of callers
     * are combined into one wider bar. It converts it from {@link TopDownNode} as it's similar to FlameChart and
     * building a {@link TopDownNode} instance only on creation gives a performance improvement in every update.
     */
    private CaptureNode convertToFlameChart(@NotNull TopDownNode topDown, double start, int depth) {
      assert topDown.getTotal() > 0;

      CaptureNode node = new CaptureNode(topDown.getNodes().get(0).getData());
      node.setFilterType(topDown.getNodes().get(0).getFilterType());
      node.setStartGlobal((long)start);
      node.setStartThread((long)start);
      node.setEndGlobal((long)(start + topDown.getTotal()));
      node.setEndThread((long)(start + topDown.getTotal()));

      node.setDepth(depth);

      for (TopDownNode child : topDown.getChildren()) {
        child.update(mySelectionRange);
      }

      List<TopDownNode> sortedChildren = new ArrayList<>(topDown.getChildren());
      // When we display a topdown node in the ui, its sorting handled by the table's sorting mechanism.
      // Conversely, in the flame chart we take care of sorting.
      // List#sort api is stable, i.e it keeps order of the appearance if sorting arguments are equal.
      sortedChildren.sort((o1, o2) -> {
        int cmp = Boolean.compare(o1.isUnmatched(), o2.isUnmatched());
        return cmp == 0 ? Double.compare(o2.getTotal(), o1.getTotal()) : cmp;
      });

      for (TopDownNode child : sortedChildren) {
        if (child.getTotal() == 0) {
          // Sorted in descending order, so starting from now every child's total is zero.
          continue;
        }
        node.addChild(convertToFlameChart(child, start, depth + 1));
        start += child.getTotal();
      }

      return node;
    }
  }
}
