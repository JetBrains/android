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

import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.CpuProfiler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TLongArrayList;

import java.util.ArrayList;
import java.util.List;

public final class ThreadStatesDataModel implements DataSeries<CpuProfiler.ThreadActivity.State> {

  private final String myName;

  // Process ID (a.k.a: tid)
  private final int myPid;

  private final List<SeriesData<CpuProfiler.ThreadActivity.State>> myThreadStates = new ArrayList<>();

  /**
   * Timestamps of the thread state changes.
   */
  private final TLongArrayList myTimestamps = new TLongArrayList();

  public ThreadStatesDataModel(String name, int pid) {
    myName = name;
    myPid = pid;
  }

  public void addState(CpuProfiler.ThreadActivity.State newState, long timestamp) {
    myThreadStates.add(new SeriesData<CpuProfiler.ThreadActivity.State>(timestamp, newState));
  }

  public String getName() {
    return myName;
  }

  public int getPid() {
    return myPid;
  }

  @Override
  public ImmutableList<SeriesData<CpuProfiler.ThreadActivity.State>> getDataForXRange(Range xRange) {
    return ContainerUtil.immutableList(myThreadStates);
  }
}