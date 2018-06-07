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
package com.android.tools.profilers.event;

import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler.EventStartRequest;
import com.android.tools.profiler.proto.EventProfiler.EventStopRequest;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfiler;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class EventProfiler extends StudioProfiler {
  public EventProfiler(@NotNull StudioProfilers profilers) {
    super(profilers);
  }

  @Override
  public ProfilerMonitor newMonitor() {
    return new EventMonitor(myProfilers);
  }

  @Override
  public void startProfiling(Common.Session session, Common.Process process) {
    myProfilers.getClient().getEventClient().startMonitoringApp(EventStartRequest.newBuilder().setSession(session).build());
  }

  @Override
  public void stopProfiling(Common.Session session, Common.Process process) {
    myProfilers.getClient().getEventClient().stopMonitoringApp(EventStopRequest.newBuilder().setSession(session).build());
  }
}
