/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.adtui.model.formatter.PercentAxisFormatter;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.VisualNodeCaptureNode;
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * This class is the base model for any {@link CpuAnalysisTabModel}'s that are backed by a {@link CaptureDetails}.
 * The title is used as both the tab title, and the tab tool tip.
 */
public class CpuAnalysisChartModel<T> extends CpuAnalysisTabModel<T> {
  public enum Aspect {
    CLOCK_TYPE
  }

  private static final Map<Type, CaptureDetails.Type> TAB_TYPE_TO_DETAIL_TYPE = ImmutableMap.of(
    Type.FLAME_CHART, CaptureDetails.Type.FLAME_CHART,
    Type.TOP_DOWN, CaptureDetails.Type.TOP_DOWN,
    Type.BOTTOM_UP, CaptureDetails.Type.BOTTOM_UP
  );

  private final CpuCapture myCapture;
  private final Range mySelectionRange;
  private final CaptureDetails.Type myDetailsType;
  private final AxisComponentModel myAxisComponentModel;
  private final Function<T, Collection<CaptureNode>> myCaptureNodesExtractor;
  @NotNull private final Range myClampedSelectionRange;
  @NotNull private final Range myCaptureConvertedRange;
  @NotNull private final AspectObserver myObserver = new AspectObserver();
  @NotNull private final AspectModel<Aspect> myAspectModel = new AspectModel<>();
  @NotNull private ClockType myClockType;

  /**
   * @param captureNodesExtractor a function that extracts capture nodes from the analysis object.
   */
  public CpuAnalysisChartModel(@NotNull Type tabType,
                               @NotNull Range selectionRange,
                               @NotNull CpuCapture capture,
                               @NotNull Function<T, Collection<CaptureNode>> captureNodesExtractor) {
    super(tabType);
    assert TAB_TYPE_TO_DETAIL_TYPE.containsKey(tabType);
    myCapture = capture;
    mySelectionRange = selectionRange;
    // Need to clone the selection range since the ClampedAxisComponent modifies the range. Without this it will cause weird selection
    // behavior in the SelectionComponent / Minimap.
    myClampedSelectionRange = new Range(selectionRange);
    myCaptureConvertedRange = new Range();
    selectionRange.addDependency(myObserver).onChange(Range.Aspect.RANGE, this::selectionRangeSync);
    myClampedSelectionRange.addDependency(myObserver).onChange(Range.Aspect.RANGE, this::updateCaptureConvertedRange);
    myCaptureConvertedRange.addDependency(myObserver).onChange(Range.Aspect.RANGE, this::updateSelectionRange);

    myDetailsType = TAB_TYPE_TO_DETAIL_TYPE.get(tabType);
    myCaptureNodesExtractor = captureNodesExtractor;
    myAxisComponentModel = new ClampedAxisComponentModel.Builder(myClampedSelectionRange, new PercentAxisFormatter(5, 10)).build();
    setClockType(ClockType.GLOBAL);
  }

  private void selectionRangeSync() {
    myClampedSelectionRange.set(mySelectionRange);
  }

  @NotNull
  public CaptureDetails createDetails() {
    return applyFilterAndCreateDetails(Filter.EMPTY_FILTER).getCaptureDetails();
  }

  /**
   * Create capture details from the chart model with a filter applied.
   *
   * @return capture details and filter result.
   */
  @NotNull
  public CaptureDetailsWithFilterResult applyFilterAndCreateDetails(@NotNull Filter filter) {
    List<CaptureNode> nodes = collectCaptureNodes();
    FilterResult combinedResult = nodes.stream()
      .map(node -> node.applyFilter(filter))
      .reduce(FilterResult::combine)
      .orElseGet(FilterResult::new);
    return new CaptureDetailsWithFilterResult(myDetailsType.build(myCaptureConvertedRange, nodes, myCapture), combinedResult);
  }

  @NotNull
  public CaptureDetails.Type getDetailsType() {
    return myDetailsType;
  }

  @NotNull
  public AxisComponentModel getAxisComponentModel() {
    return myAxisComponentModel;
  }

  public boolean isCaptureDualClock() {
    return myCapture.isDualClock();
  }

  @NotNull
  public List<ClockType> getClockTypes() {
    return ImmutableList.of(ClockType.GLOBAL, ClockType.THREAD);
  }

  @VisibleForTesting
  public Range getCaptureConvertedRange() {
    return myCaptureConvertedRange;
  }

  @NotNull
  public ClockType getClockType() {
    return myClockType;
  }

  public void setClockType(@NotNull ClockType clockType) {
    if (myClockType == clockType || (!isCaptureDualClock() && clockType == ClockType.THREAD)) {
      return;
    }
    myClockType = clockType;
    myCapture.updateClockType(clockType);
    updateCaptureConvertedRange();
    myAspectModel.changed(Aspect.CLOCK_TYPE);
  }

  @NotNull
  public AspectModel<Aspect> getAspectModel() {
    return myAspectModel;
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
    Range selection = myClampedSelectionRange;
    ClockType clockType = getClockType();
    if (clockType == ClockType.GLOBAL || getDataSeries().isEmpty()) {
      setConvertedRange(selection);
      return;
    }
    CaptureNode node = myCaptureNodesExtractor.apply(getDataSeries().get(0)).iterator().next();
    double convertedMin = node.getStartThread() + node.threadGlobalRatio() * (selection.getMin() - node.getStartGlobal());
    double convertedMax = convertedMin + node.threadGlobalRatio() * selection.getLength();
    setConvertedRange(new Range(convertedMin, convertedMax));
  }

  /**
   * Updates the selection range based on the converted range in case THREAD clock is being used.
   */
  private void updateSelectionRange() {
    // TODO: improve performance of range conversion.
    ClockType clockType = getClockType();
    if (clockType == ClockType.GLOBAL || getDataSeries().isEmpty()) {
      setSelectionRange(myCaptureConvertedRange);
      return;
    }
    // Use the ratio of the first node.
    CaptureNode node = myCaptureNodesExtractor.apply(getDataSeries().get(0)).iterator().next();
    double threadToGlobal = 1 / node.threadGlobalRatio();
    double convertedMin = node.getStartGlobal() + threadToGlobal * (myCaptureConvertedRange.getMin() - node.getStartThread());
    double convertedMax = convertedMin + threadToGlobal * myCaptureConvertedRange.getLength();
    setSelectionRange(new Range(convertedMin, convertedMax));
  }

  /**
   * Converted range updates selection range and vice-versa.
   * <p>
   * If it's almost identical to the selection range, don't update it.
   * This prevents from updating each other in a loop.
   */
  private void setSelectionRange(Range range) {
    if (!myClampedSelectionRange.isSameAs(range)) {
      myClampedSelectionRange.set(range);
    }
  }

  /**
   * Converted range updates selection range and vice-versa.
   * <p>
   * If it's almost identical to the range, don't update it.
   * This prevents from updating each other in a loop.
   */
  private void setConvertedRange(Range range) {
    if(!myCaptureConvertedRange.isSameAs(range)) {
      myCaptureConvertedRange.set(range);
    }
  }

  /**
   * Returns a {@link VisualNodeCaptureNode} as the root node. This is done as element returned by the {@link #getDataSeries()}} is expected
   * to contain the thread root {@link CaptureNode}. When selecting multiple elements we do not show the thread node. This means we loop
   * each thread root and add all children to our new {@link VisualNodeCaptureNode} root.
   * Note: As we modify the selection model we may want to revisit this behavior for individually selected threads.
   */
  @NotNull
  private List<CaptureNode> collectCaptureNodes() {
    // Data series contains each thread selected. For each thread we grab the children and add them to our root.
    return getDataSeries().stream().map(myCaptureNodesExtractor).flatMap(Collection::stream).collect(Collectors.toList());
  }

  /**
   * Helper class to hold both a {@link CaptureDetails} and {@link FilterResult}.
   */
  static final class CaptureDetailsWithFilterResult {
    @NotNull private final CaptureDetails myCaptureDetails;
    @NotNull private final FilterResult myFilterResult;

    private CaptureDetailsWithFilterResult(@NotNull CaptureDetails captureDetails, @NotNull FilterResult filterResult) {
      myCaptureDetails = captureDetails;
      myFilterResult = filterResult;
    }

    @NotNull
    CaptureDetails getCaptureDetails() {
      return myCaptureDetails;
    }

    @NotNull
    FilterResult getFilterResult() {
      return myFilterResult;
    }
  }
}
