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
import javax.swing.DefaultListModel;
import org.jetbrains.annotations.NotNull;

/**
 * List model that manages CpuState information. When a {@link SystemTraceCpuCapture} is selected this class is responsible for
 * updating the list model with each cpu's data series.
 */
public class CpuKernelModel extends DefaultListModel<CpuKernelModel.CpuState> {
  @NotNull private final CpuProfilerStage myStage;

  @NotNull private final Range myRange;

  @NotNull private final AspectObserver myAspectObserver;

  public CpuKernelModel(@NotNull Range range, @NotNull CpuProfilerStage stage) {
    myRange = range;
    myStage = stage;
    myAspectObserver = new AspectObserver();
    myStage.getAspect().addDependency(myAspectObserver).onChange(CpuProfilerAspect.CAPTURE_SELECTION, this::captureStateChanged);
    range.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::contentsChanged);
  }

  /**
   * When the capture changes update the CPU series. We remove all CPU series if the capture is not an Atrace capture.
   */
  private void captureStateChanged() {
    removeAllElements();
    CpuCapture capture = myStage.getCapture();
    if (capture != null && capture.getSystemTraceData() != null) {
      CpuSystemTraceData systemTraceData = capture.getSystemTraceData();
      int count = systemTraceData.getCpuCount();
      for (int i = 0; i < count; i++) {
        addElement(new CpuState(i, systemTraceData));
      }
    }
    contentsChanged();
  }

  private void contentsChanged() {
    fireContentsChanged(this, 0, getSize());
  }

  public class CpuState {
    private final int myCpuId;
    @NotNull
    private final DataSeries<CpuThreadSliceInfo> myCpuStateDataSeries;
    @NotNull
    private final StateChartModel<CpuThreadSliceInfo> myModel;

    public CpuState(int cpuId, @NotNull CpuSystemTraceData systemTraceData) {
      myCpuId = cpuId;
      myModel = new StateChartModel<>();
      myCpuStateDataSeries = new LazyDataSeries<>(() -> systemTraceData.getCpuThreadSliceInfoStates(myCpuId));
      // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
      myModel.addSeries(new RangedSeries<>(myRange, myCpuStateDataSeries));
    }

    public int getCpuId() {
      return myCpuId;
    }

    @NotNull
    public DataSeries<CpuThreadSliceInfo> getSeries() {
      return myCpuStateDataSeries;
    }

    @NotNull
    public StateChartModel<CpuThreadSliceInfo> getModel() {
      return myModel;
    }
  }
}
