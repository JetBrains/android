/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerAspect;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.LazyDataSeries;
import java.util.concurrent.TimeUnit;
import javax.swing.DefaultListModel;
import org.jetbrains.annotations.NotNull;

public class CpuFramesModel extends DefaultListModel<CpuFramesModel.FrameState> {
  @NotNull private final CpuProfilerStage myStage;

  @NotNull private final Range myRange;

  @NotNull private final AspectObserver myAspectObserver;

  /**
   * The default value such that any frame taking longer than this value will be marked as bad.
   */
  //TODO (b/74404740): Make this configurable.
  public static final long SLOW_FRAME_RATE_US = TimeUnit.MILLISECONDS.toMicros(17);

  public CpuFramesModel(@NotNull Range range, @NotNull CpuProfilerStage stage) {
    myRange = range;
    myStage = stage;
    myAspectObserver = new AspectObserver();
    myStage.getAspect().addDependency(myAspectObserver).onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::captureStateChanged);
    range.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::contentsChanged);
  }

  /**
   * When the capture changes update the frames series.
   */
  private void captureStateChanged() {
    removeAllElements();
    CpuCapture capture = myStage.getCapture();
    if (capture != null && capture.getSystemTraceData() != null) {
      // For now we hard code the main thread, and the render thread frame information.
      addElement(new FrameState(
        "Main", capture.getMainThreadId(), SystemTraceFrame.FrameThread.MAIN,
        capture.getSystemTraceData(), myRange));
      addElement(new FrameState(
        "Render", capture.getSystemTraceData().getRenderThreadId(), SystemTraceFrame.FrameThread.RENDER,
        capture.getSystemTraceData(), myRange));
    }
    contentsChanged();
  }

  private void contentsChanged() {
    fireContentsChanged(this, 0, getSize());
  }

  public static class FrameState {
    @NotNull
    private final DataSeries<SystemTraceFrame> myFrameDataSeries;
    @NotNull
    private final StateChartModel<SystemTraceFrame> myModel;
    private final String myThreadName;
    private final int myThreadId;

    public FrameState(String threadName,
                      int threadId,
                      @NotNull SystemTraceFrame.FrameThread threadType,
                      @NotNull CpuSystemTraceData systemTraceData,
                      @NotNull Range range) {
      myModel = new StateChartModel<>();
      myThreadName = threadName;
      myThreadId = threadId;
      myFrameDataSeries = new LazyDataSeries<>(() -> systemTraceData.getFrames(threadType));
      // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
      myModel.addSeries(new RangedSeries<>(range, myFrameDataSeries));
    }

    public String getThreadName() {
      return myThreadName;
    }

    public int getThreadId() {
      return myThreadId;
    }

    @NotNull
    public DataSeries<SystemTraceFrame> getSeries() {
      return myFrameDataSeries;
    }

    @NotNull
    public StateChartModel<SystemTraceFrame> getModel() {
      return myModel;
    }
  }
}
