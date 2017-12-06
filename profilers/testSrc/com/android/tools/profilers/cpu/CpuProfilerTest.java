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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class CpuProfilerTest {

  private static final int FAKE_PID = 1234;

  private static final Common.Session FAKE_SESSION = Common.Session.newBuilder().setSessionId(4321).setPid(FAKE_PID).build();

  private final FakeCpuService myService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuProfilerTest", myService);

  private CpuProfiler myCpuProfiler;

  @Before
  public void setUp() {
    myCpuProfiler = new CpuProfiler(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer()));
  }

  @Test
  public void newMonitor() {
    ProfilerMonitor monitor = myCpuProfiler.newMonitor();
    assertNotNull(monitor);
    assertTrue(monitor instanceof CpuMonitor);
  }

  @Test
  public void startProfilingCallStartMonitoringAppId() {
    myCpuProfiler.startProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the start monitoring request
    assertEquals(FAKE_SESSION, myService.getSession());
  }

  @Test
  public void stopProfilingCallStopMonitoringAppId() {
    myCpuProfiler.stopProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the stop monitoring request
    assertEquals(FAKE_SESSION, myService.getSession());
  }
}
