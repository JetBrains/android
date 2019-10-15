/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manages states of the selected capture, such as current select thread, capture details (i.e top down tree, bottom up true, chart).
 * When a state changes, this class lets all view know about the changes they're interested in.
 */
public class CaptureModel {
  /**
   * A negligible number. It is used for comparision.
   */
  private static final double EPSILON = 1e-5;

  /**
   * Negative number used when no thread is selected.
   */
  public static final int NO_THREAD = -1;

  private static final Map<CaptureDetails.Type, Consumer<FeatureTracker>> DETAILS_TRACKERS = ImmutableMap.of(
    CaptureDetails.Type.TOP_DOWN, FeatureTracker::trackSelectCaptureTopDown,
    CaptureDetails.Type.BOTTOM_UP, FeatureTracker::trackSelectCaptureBottomUp,
    CaptureDetails.Type.CALL_CHART, FeatureTracker::trackSelectCaptureCallChart,
    CaptureDetails.Type.FLAME_CHART, FeatureTracker::trackSelectCaptureFlameChart,
    // We don't track usage for experimental features
    CaptureDetails.Type.RENDER_AUDIT, tracker -> {}
  );

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
  @NotNull
  private Filter myFilter = Filter.EMPTY_FILTER;

  @Nullable
  private CaptureDetails myDetails;

  private int myTotalNodeCount;

  private int myFilterNodeCount;

  /**
   * Reference to a selection range converted to ClockType.THREAD.
   */
  private final Range myCaptureConvertedRange;

  public CaptureModel(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myCaptureConvertedRange = new Range();
    myThread = NO_THREAD;

    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    selection.addDependency(myStage.getAspect()).onChange(Range.Aspect.RANGE, this::updateCaptureConvertedRange);
    myCaptureConvertedRange.addDependency(myStage.getAspect()).onChange(Range.Aspect.RANGE, this::updateSelectionRange);
  }

  /**
   * @return true, if the {@param capture} is different than the current capture.
   */
  public boolean setCapture(@Nullable CpuCapture capture) {
    if (myCapture == capture) {
      return false;
    }
    myCapture = capture;
    if (myCapture != null) {
      // If a thread was already selected, keep the selection. Otherwise select the capture main thread.
      setThread(myThread != NO_THREAD ? myThread : capture.getMainThreadId());
      // Not all captures support both clocks, this check allows us to keep thread type clocks
      // for captures that support it, otherwise we use the global clock.
      if (myCapture.isDualClock()) {
        setClockType(myClockType);
      }
      else {
        setClockType(ClockType.GLOBAL);
      }
    }
    else {
      setThread(NO_THREAD);
    }
    rebuildDetails();
    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE_SELECTION);
    return true;
  }

  @Nullable
  public CpuCapture getCapture() {
    return myCapture;
  }

  public void setThread(int thread) {
    if (myThread == thread) {
      return;
    }
    myThread = thread;
    rebuildDetails();
    myStage.getAspect().changed(CpuProfilerAspect.SELECTED_THREADS);
  }

  public int getThread() {
    return myThread;
  }

  public void setClockType(@NotNull ClockType type) {
    if (myClockType == type || (myCapture != null && !myCapture.isDualClock() && type == ClockType.THREAD)) {
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
  public ClockType getClockType() {
    return myClockType;
  }

  public void setFilter(@NotNull Filter filter) {
    if (Objects.equals(filter, myFilter)) {
      return;
    }
    myFilter = filter;
    rebuildDetails();
  }

  @NotNull
  public Filter getFilter() {
    return myFilter;
  }

  public void setDetails(@Nullable CaptureDetails.Type type) {
    if (type != null && myDetails != null && type == myDetails.getType()) {
      return;
    }

    FeatureTracker tracker = myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
    if (type != null) {
      DETAILS_TRACKERS.get(type).accept(tracker);
    }
    rebuildDetails(type);
  }

  @Nullable
  public CaptureDetails getDetails() {
    return myDetails;
  }

  /**
   * Helper function to change the {@link CaptureDetails}.
   *
   * @param suggestedType The {@link CaptureDetails.Type} to change to. If suggestedType is not null the new type will be that type.
   *                      If suggestedType is null the last capture details type is used.
   *                      If no last details type is set the {@link CaptureDetails.Type.CALL_CHART} is used by default.
   */
  private void rebuildDetails(@Nullable CaptureDetails.Type suggestedType) {
    updateCaptureConvertedRange();
    myTotalNodeCount = 0;
    myFilterNodeCount = 0;
    if (myCapture != null) {
      // Grab the currently selected thread and apply any filters the user set.
      CaptureNode node = getNode();
      if (node != null) {
        applyFilter(node, false);
      }
      if (suggestedType == null) {
        suggestedType = myDetails == null ? CaptureDetails.Type.CALL_CHART : myDetails.getType();
      }
      myDetails = suggestedType.build(myCaptureConvertedRange, node, myCapture);
    }
    else {
      // If we don't have a capture clear the filter state and the details.
      myFilter = Filter.EMPTY_FILTER;
      myDetails = null;
    }
    // Let everyone know the state of details has changed. This needs to be done after we set myDetails.
    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE_DETAILS);
  }

  private void rebuildDetails() {
    rebuildDetails(null);
  }

  @Nullable
  private CaptureNode getNode() {
    return myCapture != null ? myCapture.getCaptureNode(myThread) : null;
  }

  public int getNodeCount() {
    return myTotalNodeCount;
  }

  public int getFilterNodeCount() {
    return myFilterNodeCount;
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
    myTotalNodeCount++;
    if (nodeExactMatch) {
      myFilterNodeCount++;
    }
    for (CaptureNode child : node.getChildren()) {
      applyFilter(child, matches);
      if (!child.isUnmatched()) {
        allChildrenUnmatch = false;
      }
    }

    if (!matches && allChildrenUnmatch) {
      node.setFilterType(CaptureNode.FilterType.UNMATCH);
    }
    else if (nodeExactMatch && !myFilter.isEmpty()) {
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
   * <p>
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
   * <p>
   * If it's almost identical to the range, don't update it.
   * This prevents from updating each other in a loop.
   */
  private void setConvertedRange(double min, double max) {
    if (Math.abs(myCaptureConvertedRange.getMin() - min) > EPSILON || Math.abs(myCaptureConvertedRange.getMax() - max) > EPSILON) {
      myCaptureConvertedRange.set(min, max);
    }
  }
}
