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
package com.android.tools.profilers;

import com.android.tools.profiler.proto.CpuProfiler;

import java.util.LinkedList;
import java.util.List;

public class StudioMonitor extends StudioProfilerStage {
  private final StudioProfiler myProfiler;
  private int myProcessId;
  private List<ProfilerMonitor> myMonitors;

  public StudioMonitor(StudioProfiler profiler) {
    myProfiler = profiler;
    myMonitors = new LinkedList<>();
  }

  @Override
  public void enter() {
    myProcessId = myProfiler.getProcessId();
    myMonitors.clear();
    for (BaseProfiler profiler : myProfiler.getProfilers()) {
      myMonitors.add(profiler.newMonitor(myProcessId));
    }
  }

  @Override
  public void exit() {
    for (ProfilerMonitor monitor : myMonitors) {
      monitor.stop();
    }
  }

  public List<ProfilerMonitor> getMonitors() {
    return myMonitors;
  }
}
