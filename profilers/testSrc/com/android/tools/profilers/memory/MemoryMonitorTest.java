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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.NullMonitorStage;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.google.common.truth.Truth;
import org.junit.Rule;
import org.junit.Test;

public class MemoryMonitorTest {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeMemoryService myMemoryService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryMonitorTestChannel", myMemoryService, new FakeTransportService(myTimer), new FakeProfilerService(myTimer),
                        new FakeEventService(), new FakeCpuService());

  @Test
  public void testName() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    MemoryMonitor monitor = new MemoryMonitor(profilers);
    Truth.assertThat(monitor.getName()).isEqualTo("MEMORY");
  }

  @Test
  public void testExpand() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    MemoryMonitor monitor = new MemoryMonitor(profilers);
    Truth.assertThat(profilers.getStage().getClass()).isEqualTo(NullMonitorStage.class);
    monitor.expand();
    Truth.assertThat(profilers.getStage()).isInstanceOf(MainMemoryProfilerStage.class);
  }
}
