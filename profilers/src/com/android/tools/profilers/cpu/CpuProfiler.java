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

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler.CpuStartRequest;
import com.android.tools.profiler.proto.CpuProfiler.CpuStopRequest;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CpuProfiler extends StudioProfiler {
  public CpuProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new CpuMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session, Common.Process process) {
    // TODO: handle different status of the response
    myProfilers.getClient().getCpuClient().startMonitoringApp(CpuStartRequest.newBuilder()
                                                                .setProcessId(process.getPid())
                                                                .setSession(session).build());
  }

  @Override
  public void stopProfiling(Common.Session session, Common.Process process) {
    // TODO: handle different status of the response
    myProfilers.getClient().getCpuClient().stopMonitoringApp(CpuStopRequest.newBuilder()
                                                               .setProcessId(process.getPid())
                                                               .setSession(session).build());
  }
}
