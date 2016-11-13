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

import java.util.LinkedList;
import java.util.List;

import static com.android.tools.profilers.StudioProfilers.INVALID_PROCESS_ID;

public class StudioMonitorStage extends Stage {
  private List<ProfilerMonitor> myMonitors;

  public StudioMonitorStage(StudioProfilers profiler) {
    super(profiler);
    myMonitors = new LinkedList<>();
  }

  @Override
  public void enter() {
    myMonitors.clear();
    int processId = getStudioProfilers().getProcessId();
    if (processId != INVALID_PROCESS_ID) {
      for (StudioProfiler profiler : getStudioProfilers().getProfilers()) {
        myMonitors.add(profiler.newMonitor());
      }
    }
  }

  @Override
  public void exit() {
  }

  public List<ProfilerMonitor> getMonitors() {
    return myMonitors;
  }
}
