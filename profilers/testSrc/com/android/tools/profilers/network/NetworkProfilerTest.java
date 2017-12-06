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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.android.tools.profilers.ProfilersTestData.SESSION_DATA;
import static org.junit.Assert.*;

public class NetworkProfilerTest {
  private final FakeNetworkService myService = FakeNetworkService.newBuilder().build();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("NetworkProfilerTest", myService);

  private NetworkProfiler myProfiler;

  @Before
  public void setUp() {
    myProfiler = new NetworkProfiler(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer()));
  }

  @Test
  public void newMonitor() {
    ProfilerMonitor monitor = myProfiler.newMonitor();
    assertNotNull(monitor);
    assertTrue(monitor instanceof NetworkMonitor);
  }

  @Test
  public void startMonitoring() {
    myProfiler.startProfiling(SESSION_DATA);
    assertEquals(SESSION_DATA, myService.getSession());
  }

  @Test
  public void stopMonitoring() {
    myProfiler.stopProfiling(SESSION_DATA);
    assertEquals(SESSION_DATA, myService.getSession());
  }
}