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
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class CpuProfilerTest {

  private static final int FAKE_PID = 42;

  private final FakeCpuService myService = new FakeCpuService();

  private Profiler.Process FAKE_PROCESS = Profiler.Process.newBuilder().setPid(FAKE_PID).setName("FakeProcess").build();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuProfilerTest", myService);

  private CpuProfiler myCpuProfiler;

  @Before
  public void setUp() throws Exception {
    myCpuProfiler = new CpuProfiler(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer()));
  }

  @Test
  public void newMonitor() {
    ProfilerMonitor monitor = myCpuProfiler.newMonitor();
    assertNotNull(monitor);
    assertTrue(monitor instanceof CpuMonitor);
  }

  @Test
  public void startProfilingCallStartMonitoringAppId() throws InterruptedException {
    myCpuProfiler.startProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    // Make sure the pid of the service was set to FAKE_PID by the start monitoring request
    assertEquals(FAKE_PID, myService.getProcessId());
  }

  @Test
  public void stopProfilingCallStopMonitoringAppId() throws InterruptedException {
    myCpuProfiler.stopProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    // Make sure the pid of the service was set to FAKE_PID by the stop monitoring request
    assertEquals(FAKE_PID, myService.getProcessId());
  }
}
