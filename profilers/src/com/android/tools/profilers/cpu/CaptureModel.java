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

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Manages states of the selected capture, such as current select thread, capture details (i.e top down tree, bottom up true, chart).
 * When a state changes, this class lets all view know about the changes they're interested in.
 */
class CaptureModel {
  @NotNull
  private final CpuProfilerStage myStage;

  @Nullable
  private CpuCapture myCapture;

  private int myThread;

  @NotNull
  private ClockType myClockType = ClockType.GLOBAL;

  @Nullable
  private Details myDetails;

  /**
   * Reference to a selection range converted to ClockType.THREAD.
   */
  private final Range myCaptureConvertedRange;

  /**
   * Whether selection range update was triggered by an update in the converted range.
   * Converted range updates selection range and vice-versa. To avoid stack overflow,
   * we avoid updating the converted range in a loop.
   */
  private boolean myIsConvertedRangeUpdatingSelection;

  CaptureModel(@NotNull CpuProfilerStage stage) {
    myStage = stage;
    myCaptureConvertedRange = new Range();

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
      myCapture.updateClockType(myClockType);
    }
    rebuildDetails();
    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE);
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
      buildDetails(myDetails == null ? Details.Type.TOP_DOWN : myDetails.getType());
    }
  }

  private void buildDetails(@Nullable Details.Type type) {
    updateCaptureConvertedRange();
    myDetails = type != null ? type.build(myCaptureConvertedRange, getNode()) : null;
    myStage.getAspect().changed(CpuProfilerAspect.CAPTURE_DETAILS);
  }

  @Nullable
  private HNode<MethodModel> getNode() {
    return myCapture != null ? myCapture.getCaptureNode(myThread) : null;
  }

  /**
   * When using ClockType.THREAD, we need to scale the selection to actually select a relevant range in the capture.
   * That happens because selection is based on wall-clock time, which is usually way greater than thread time.
   * As the two types of clock are synced at start time, making a selection starting at a time
   * greater than (start + thread time length) will result in no feedback for the user, which is wrong.
   * Therefore, we scale the selection so we can provide relevant thread time data as the user changes selection.
   */
  private void updateCaptureConvertedRange() {
    if (myIsConvertedRangeUpdatingSelection) {
      myIsConvertedRangeUpdatingSelection = false;
      return;
    }
    myIsConvertedRangeUpdatingSelection = true;

    // TODO: improve performance of select range conversion.
    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    HNode<MethodModel> topLevelNode;
    ClockType clockType = getClockType();
    CpuCapture capture = getCapture();
    if (clockType == ClockType.GLOBAL || capture == null || (topLevelNode = capture.getCaptureNode(getThread())) == null) {
      myCaptureConvertedRange.set(selection);
      return;
    }
    assert topLevelNode instanceof CaptureNode;
    CaptureNode node = (CaptureNode)topLevelNode;

    double convertedMin = node.getStartThread() + node.threadGlobalRatio() * (selection.getMin() - node.getStartGlobal());
    double convertedMax = convertedMin + node.threadGlobalRatio() * selection.getLength();
    myCaptureConvertedRange.set(convertedMin, convertedMax);
  }

  /**
   * Updates the selection range based on the converted range in case THREAD clock is being used.
   */
  private void updateSelectionRange() {
    // TODO: improve performance of range conversion.
    HNode<MethodModel> topLevelNode;
    ClockType clockType = getClockType();
    CpuCapture capture = getCapture();
    if (clockType == ClockType.GLOBAL || capture == null || (topLevelNode = capture.getCaptureNode(getThread())) == null) {
      myStage.getStudioProfilers().getTimeline().getSelectionRange().set(myCaptureConvertedRange);
      return;
    }
    assert topLevelNode instanceof CaptureNode;
    CaptureNode node = (CaptureNode)topLevelNode;

    double threadToGlobal = 1 / node.threadGlobalRatio();
    double convertedMin = node.getStartGlobal() + threadToGlobal * (myCaptureConvertedRange.getMin() - node.getStartThread());
    double convertedMax = convertedMin + threadToGlobal * myCaptureConvertedRange.getLength();
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(convertedMin, convertedMax);
  }

  public interface Details {
    enum Type {
      TOP_DOWN(TopDown::new),
      BOTTOM_UP(BottomUp::new),
      CHART(TreeChart::new);

      @NotNull
      private final BiFunction<Range, HNode<MethodModel>, Details> myBuilder;

      Type(@NotNull BiFunction<Range, HNode<MethodModel>, Details> builder) {
        myBuilder = builder;
      }

      public Details build(Range range, HNode<MethodModel> node) {
        return myBuilder.apply(range, node);
      }
    }

    Type getType();
  }

  public static class TopDown implements Details {
    @Nullable private TopDownTreeModel myModel;

    public TopDown(@NotNull Range range, @Nullable HNode<MethodModel> node) {
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

    public BottomUp(@NotNull Range range, @Nullable HNode<MethodModel> node) {
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

  public static class TreeChart implements Details {
    @NotNull private final Range myRange;
    @Nullable private HNode<MethodModel> myNode;

    public TreeChart(@NotNull Range range, @Nullable HNode<MethodModel> node) {
      myRange = range;
      myNode = node;
    }

    @NotNull
    public Range getRange() {
      return myRange;
    }

    @Nullable
    public HNode<MethodModel> getNode() {
      return myNode;
    }

    @Override
    public Type getType() {
      return Type.CHART;
    }
  }
}
