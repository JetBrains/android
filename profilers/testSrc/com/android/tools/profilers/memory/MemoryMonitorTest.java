/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.network.FakeNetworkService;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class MemoryMonitorTest {

  private final FakeProfilerService myProfilerService = new FakeProfilerService(true);
  private final FakeMemoryService myMemoryService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryMonitorTestChannel", myMemoryService, myProfilerService,
                                                             new FakeEventService(), new FakeCpuService(),
                                                             FakeNetworkService.newBuilder().build());

  @Test
  public void testName() {
    MemoryMonitor monitor = new MemoryMonitor(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices()));
    assertEquals("MEMORY", monitor.getName());
  }

  @Test
  public void testExpand() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    MemoryMonitor monitor = new MemoryMonitor(profilers);
    assertEquals(profilers.getStage().getClass(), NullMonitorStage.class);
    monitor.expand();
    assertThat(profilers.getStage(), instanceOf(MemoryProfilerStage.class));
  }

  @Test
  public void testLiveAllocationTrackingOnAgentAttach() {
    FakeIdeProfilerServices ideProfilerServices = new FakeIdeProfilerServices();
    FakeTimer timer = new FakeTimer();

    // Note that MemoryMonitor is created by StudioMonitorStage when the process gets selected after the fake timer tick.
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), ideProfilerServices, timer);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);

    assertTrue(profilers.getStage() instanceof StudioMonitorStage);
    assertFalse(profilers.isAgentAttached());
    assertEquals(0, myMemoryService.getTrackAllocationCount());

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertTrue(profilers.isAgentAttached());
    assertEquals(0, myMemoryService.getTrackAllocationCount());
  }
}
