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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.BaseCpuCapture;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.ThreadState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class SystemTraceCpuCapture extends BaseCpuCapture implements CpuSystemTraceData {
  @NotNull
  private final Map<Integer, List<SeriesData<ThreadState>>> myThreadStateDataSeries;

  @NotNull
  private final Map<Integer, List<SeriesData<CpuThreadSliceInfo>>> myCpuThreadSliceInfoStates;

  @NotNull
  private final List<SeriesData<Long>> myCpuUtilizationSeries;

  @NotNull
  private final Map<String, List<SeriesData<Long>>> myProcessMemoryCountersMap;

  @NotNull
  private final List<Map<String, List<SeriesData<Long>>>> myCpuCounters;

  private final boolean myIsMissingData;

  @NotNull
  private final SystemTraceFrameManager myFrameManager;

  @NotNull
  private final SystemTraceSurfaceflingerManager mySurfaceflingerManager;

  public SystemTraceCpuCapture(long traceId,
                               @NotNull SystemTraceModelAdapter model,
                               @NotNull Map<CpuThreadInfo, CaptureNode> captureNodes,
                               @NotNull Map<Integer, List<SeriesData<ThreadState>>> threadStateData,
                               @NotNull Map<Integer, List<SeriesData<CpuThreadSliceInfo>>> cpuSchedData,
                               @NotNull List<SeriesData<Long>> cpuUtilizationData,
                               @NotNull List<Map<String, List<SeriesData<Long>>>> cpuCounters,
                               @NotNull Map<String, List<SeriesData<Long>>> processMemoryCountersMap,
                               @NotNull SystemTraceFrameManager frameManager,
                               @NotNull SystemTraceSurfaceflingerManager surfaceflingerManager,
                               @NotNull Range initialViewRangeUs) {
    super(traceId, model.getSystemTraceTechnology(),
          new Range(model.getCaptureStartTimestampUs(), model.getCaptureEndTimestampUs()),
          captureNodes);

    myThreadStateDataSeries = threadStateData;
    myCpuThreadSliceInfoStates = cpuSchedData;
    myCpuUtilizationSeries = cpuUtilizationData;
    myCpuCounters = cpuCounters;
    myProcessMemoryCountersMap = processMemoryCountersMap;
    myIsMissingData = model.isCapturePossibleCorrupted();

    myFrameManager = frameManager;
    mySurfaceflingerManager = surfaceflingerManager;
    // Set the view range of the capture timeline to our initial view range, this is used later by the UI to set the initial view.
    getTimeline().getViewRange().set(initialViewRangeUs.getMin(), initialViewRangeUs.getMax());
  }

  @Override
  public CpuSystemTraceData getSystemTraceData() {
    return this;
  }

  /**
   * The thread states are computed from the sched_switch trace line reported by an atrace capture.
   * Atrace reports a sched_switch event each time the thread state changes, because of this the thread states
   * reported here are more accurate than the ones sampled via perfd.
   */
  @Override
  @NotNull
  public List<SeriesData<ThreadState>> getThreadStatesForThread(int threadId) {
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

  @NotNull
  @Override
  public List<Map<String, List<SeriesData<Long>>>> getCpuCounters() {
    return myCpuCounters;
  }

  @Override
  public int getCpuCount() {
    return myCpuThreadSliceInfoStates.size();
  }

  @NotNull
  @Override
  public Map<String, List<SeriesData<Long>>> getMemoryCounters() {
    return myProcessMemoryCountersMap;
  }

  @Override
  public boolean isMissingData() {
    return myIsMissingData;
  }

  @Override
  @NotNull
  public List<SeriesData<SystemTraceFrame>> getFrames(@NotNull SystemTraceFrame.FrameThread threadType) {
    return myFrameManager.getFrames(threadType);
  }

  @NotNull
  @Override
  public List<SeriesData<SurfaceflingerEvent>> getSurfaceflingerEvents() {
    return mySurfaceflingerManager.getSurfaceflingerEvents();
  }

  @NotNull
  @Override
  public List<SeriesData<Long>> getVsyncCounterValues() {
    return mySurfaceflingerManager.getVsyncCounterValues();
  }

  @NotNull
  @Override
  public List<SeriesData<Long>> getBufferQueueCounterValues() {
    return mySurfaceflingerManager.getBufferQueueValues();
  }

  @Override
  public int getRenderThreadId() {
    return myFrameManager.getRenderThreadId();
  }
}
