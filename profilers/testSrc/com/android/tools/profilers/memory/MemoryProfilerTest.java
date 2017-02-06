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
package com.android.tools.profilers.memory;

import com.android.tools.profiler.proto.*;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.FakeGrpcChannel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class MemoryProfilerTest {
  private static final int FAKE_PID = 111;

  private final FakeMemoryService myService = new FakeMemoryService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerTest", myService);

  private Profiler.Process FAKE_PROCESS = Profiler.Process.newBuilder().setPid(FAKE_PID).setName("FakeProcess").build();
  private MemoryProfiler myProfiler;

  @Before
  public void setUp() {
    myProfiler = new MemoryProfiler(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices()));
  }

  @Test
  public void startMonitoring() {
    myProfiler.startProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    assertEquals(FAKE_PID, myService.getProcessId());
  }

  @Test
  public void stopMonitoring() {
    myProfiler.stopProfiling(ProfilersTestData.SESSION_DATA, FAKE_PROCESS);
    assertEquals(FAKE_PID, myService.getProcessId());
  }
}