/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.SeriesData;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DataSeries} of {@link CpuProfilerStage.ThreadState} to represent states of threads obtained from imported CPU traces. The only
 * states of a the series should be {@link CpuProfilerStage.ThreadState#HAS_ACTIVITY} (for intervals with method trace data, indicating the
 * thread had activity) and {@link CpuProfilerStage.ThreadState#NO_ACTIVITY} (when there is no thread activity in the interval).
 */
public final class ImportedTraceThreadDataSeries extends InMemoryDataSeries<CpuProfilerStage.ThreadState> {

  /**
   * ID of the thread whose activity is represented by this {@link DataSeries}.
   */
  private final int myThreadId;
  /**
   * List of {@link SeriesData<CpuProfilerStage.ThreadState>} of the thread represented by this {@link DataSeries}.
   */
  @NotNull private final List<SeriesData<CpuProfilerStage.ThreadState>> myStates;

  public ImportedTraceThreadDataSeries(@NotNull CpuProfilerStage stage, int tid) {
    super(stage);
    myThreadId = tid;
    myStates = buildThreadStates();
  }

  /**
   * Build the thread states only once, as they don't change over time for imported captures.
   */
  private List<SeriesData<CpuProfilerStage.ThreadState>> buildThreadStates() {
    List<SeriesData<CpuProfilerStage.ThreadState>> states = new ArrayList<>();
    CpuCapture capture = myStage.getCapture();
    if (capture == null) {
      getLogger().warn("Capture is unexpectedly null and thread states could not be built.");
      return states;
    }
    CaptureNode root = capture.getCaptureNode(myThreadId);
    if (root == null) {
      getLogger().warn("Thread root node is unexpectedly null and thread states could not be built.");
      return states;
    }
    // Root is the special node representing the thread itself ranges from earliest data in root children to latest data in root children.
    // We're interested in seeing the activities of each root children instead.
    List<CaptureNode> rootChildren = root.getChildren();
    for (CaptureNode rootChild : rootChildren) {
      states.add(new SeriesData<>(rootChild.getStart(), CpuProfilerStage.ThreadState.HAS_ACTIVITY));
      states.add(new SeriesData<>(rootChild.getEnd(), CpuProfilerStage.ThreadState.NO_ACTIVITY));
    }

    return states;
  }

  @Override
  protected List<SeriesData<CpuProfilerStage.ThreadState>> inMemoryDataList() {
    return myStates;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(ImportedTraceThreadDataSeries.class);
  }
}