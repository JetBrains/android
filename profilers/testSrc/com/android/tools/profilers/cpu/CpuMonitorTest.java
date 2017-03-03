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
import com.android.tools.profilers.*;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.*;

public class CpuMonitorTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("CpuMonitorTestChannel", new FakeCpuService(), new FakeProfilerService());

  @Test
  public void testName() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    CpuMonitor monitor = new CpuMonitor(profilers);
    assertEquals("CPU", monitor.getName());
  }

  @Test
  public void testExpand() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers =new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    CpuMonitor monitor = new CpuMonitor(profilers);
    assertEquals(profilers.getStage().getClass(), NullMonitorStage.class);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    monitor.expand();
    assertThat(profilers.getStage(), instanceOf(CpuProfilerStage.class));
  }
}
