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
package com.android.tools.profilers.cpu.atrace;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.cpu.BaseCpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class AtraceCpuCapture extends BaseCpuCapture {
  @NotNull
  private final Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> myThreadStateDataSeries;

  @NotNull
  private final Map<Integer, List<SeriesData<CpuThreadSliceInfo>>> myCpuThreadSliceInfoStates;

  @NotNull
  private final List<SeriesData<Long>> myCpuUtilizationSeries;

  private final int myRenderThreadId;
  private final boolean myIsMissingData;

  @NotNull
  private final AtraceFrameManager myFrameManager;

  public AtraceCpuCapture(long traceId, @NotNull Range range, @NotNull AtraceParser parser, @NotNull AtraceFrameManager frameManager) {
    super(traceId, Cpu.CpuTraceType.ATRACE, range, parser.getCaptureTrees());

    myThreadStateDataSeries = parser.getThreadStateDataSeries();
    myCpuThreadSliceInfoStates = parser.getCpuThreadSliceInfoStates();
    myCpuUtilizationSeries = parser.getCpuUtilizationSeries();

    myRenderThreadId = parser.getRenderThreadId();
    myIsMissingData = parser.isMissingData();

    myFrameManager = frameManager;
  }

  /**
   * The thread states are computed from the sched_switch trace line reported by an atrace capture.
   * Atrace reports a sched_switch event each time the thread state changes, because of this the thread states
   * reported here are more accurate than the ones sampled via perfd.
   */
  @Override
  @NotNull
  public List<SeriesData<CpuProfilerStage.ThreadState>> getThreadStatesForThread(int threadId) {
    return myThreadStateDataSeries.getOrDefault(threadId, new ArrayList<>());
  }

  /**
   * The information is computed from the sched_switch trace line reported by atrace.
   */
  @Override
  @NotNull
  public List<SeriesData<CpuThreadSliceInfo>> getCpuThreadSliceInfoStates(int cpu) {
    return myCpuThreadSliceInfoStates.getOrDefault(cpu, new ArrayList<>());
  }

  @Override
  @NotNull
  public List<SeriesData<Long>> getCpuUtilizationSeries() {
    return myCpuUtilizationSeries;
  }

  @Override
  public int getCpuCount() {
    return myCpuThreadSliceInfoStates.size();
  }

  @Override
  public boolean isMissingData() {
    return myIsMissingData;
  }

  @Override
  @NotNull
  public List<SeriesData<AtraceFrame>> getFrames(AtraceFrame.FrameThread threadType) {
    return myFrameManager.getFrames(threadType);
  }

  @Override
  public int getRenderThreadId() {
    return myRenderThreadId;
  }
}
