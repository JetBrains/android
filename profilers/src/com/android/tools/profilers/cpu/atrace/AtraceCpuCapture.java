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

import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.model.ProcessModel;

public class AtraceCpuCapture extends CpuCapture {

  private final AtraceParser myParser;

  public AtraceCpuCapture(AtraceParser parser, long traceId) {
    super(parser, traceId, Cpu.CpuTraceType.ATRACE);
    myParser = parser;
  }

  /**
   * @param threadId Thread Id of thread requesting states for. If thread id is not found an empty list is returned.
   * @return Thread state transitions for the given thread. The thread states are computed from the
   * sched_switch trace line reported by an atrace capture. Atrace reports a sched_switch event each time the thread state changes,
   * because of this the thread states reported here are more accurate than the ones sampled via perfd.
   */
  @NotNull
  public List<SeriesData<CpuProfilerStage.ThreadState>> getThreadStatesForThread(int threadId) {
    return myParser.getThreadStateDataSeries().getOrDefault(threadId, new ArrayList<>());
  }

  /**
   * @param cpu The cpu index to get {@link CpuThreadSliceInfo} series for.
   * @return A series of {@link CpuThreadSliceInfo} information. The information is computed from the
   * sched_switch trace line reported by atrace.
   */
  @NotNull
  public List<SeriesData<CpuThreadSliceInfo>> getCpuThreadSliceInfoStates(int cpu) {
    return myParser.getCpuThreadSliceInfoStates().getOrDefault(cpu, new ArrayList<>());
  }

  /**
   * @return Cpu Utilization data series. This data series is computed from each core
   */
  @NotNull
  public List<SeriesData<Long>> getCpuUtilizationSeries() {
    return myParser.getCpuUtilizationSeries();
  }

  /**
   * @return The number of cores represented by this capture.
   */
  public int getCpuCount() {
    return myParser.getCpuThreadSliceInfoStates().size();
  }

  /**
   * @return If the capture is potentially missing data due to the capture buffer being a ring buffer.
   */
  public boolean isMissingData() {
    return myParser.isMissingData();
  }

  /**
   * @return Data series of frame perf classes sorted by frame start time.
   */
  public List<SeriesData<AtraceFrame>> getFrames(AtraceFrameFilterConfig filter) {
    return myParser.getFrames(filter);
  }

  /**
   * @return thread id of thread matching name of the render thread.
   */
  public int getRenderThreadId() {
    return myParser.getRenderThreadId();
  }

  /**
   * @return Trebuchet ProcessModel for the Surfaceflinger process or null if there is no
   * such process for this capture
   */
  @Nullable
  public ProcessModel getSurfaceflingerProcessModel() {
    return myParser.getSurfaceflingerProcessModel();
  }
}
