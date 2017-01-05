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
package com.android.tools.profilers;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

final public class StudioProfilersTest {
  private final FakeProfilerService myService = new FakeProfilerService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("StudioProfilerTestChannel", myService);

  @Test
  public void testVersion() throws Exception {
    Profiler.VersionResponse response =
      myGrpcChannel.getClient().getProfilerClient().getVersion(Profiler.VersionRequest.getDefaultInstance());
    assertEquals(FakeProfilerService.VERSION, response.getVersion());
  }

  @Test
  public void testClearedOnMonitorStage() throws Exception {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());

    profilers.setStage(new CpuProfilerStage(profilers));
    profilers.getTimeline().getSelectionRange().set(10, 10);
    profilers.setMonitoringStage();

    assertTrue(profilers.getTimeline().getSelectionRange().isEmpty());
  }

  @Test
  public void testTimeResetOnConnectedDevice() throws Exception {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub(), timer);
    int now = 42;
    myService.setTimestampNs(TimeUnit.SECONDS.toNanos(now));
    timer.tick(1);
    // TODO: The model still uses real time as time, we need to allow mocking it for testing
    //int dataNow = now - StudioProfilers.TIMELINE_BUFFER;
    //assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    //assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMax(), 0.001);
    //timer.tick(10); // Ten seconds
    //assertEquals(TimeUnit.SECONDS.toMicros(dataNow), profilers.getTimeline().getDataRange().getMin(), 0.001);
    //assertEquals(TimeUnit.SECONDS.toMicros(dataNow + 10), profilers.getTimeline().getDataRange().getMax(), 0.001);
  }
}
