// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EnergyProfiler extends StudioProfiler {

  public EnergyProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new EnergyMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session) {
    // TODO: Call real start profiling api and add test
  }

  @Override
  public void stopProfiling(Common.Session session) {
    // TODO: Call real stop profiling api and add test
  }
}
