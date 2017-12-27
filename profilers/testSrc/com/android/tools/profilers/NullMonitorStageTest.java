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
package com.android.tools.profilers;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Profiler;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NullMonitorStageTest {

  private FakeProfilerService myRpcService = new FakeProfilerService(false);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("NullMonitorStageTest", myRpcService);
  private StudioProfilers myProfilers;

  @Test
  public void testModelMessageAndTitle() {
    FakeTimer timer = new FakeTimer();
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    NullMonitorStage stage = new NullMonitorStage(myProfilers);
    // No device was added, check for the appropriate message and title
    assertEquals(stage.getMessage(), NullMonitorStage.NO_DEVICE_MESSAGE);
    assertEquals(stage.getTitle(), NullMonitorStage.ANDROID_PROFILER_TITLE);

    // Add a device
    Profiler.Device device = Profiler.Device.getDefaultInstance();
    myRpcService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Device has no valid API. Check the message and the title have changed to the appropriate ones.
    assertEquals(stage.getMessage(), NullMonitorStage.DEVICE_NOT_SUPPORTED_MESSAGE);
    assertEquals(stage.getTitle(), NullMonitorStage.DEVICE_NOT_SUPPORTED_TITLE);

    // Update the device to an API < 21
    Common.Session session = Common.Session.newBuilder()
      .setBootId(device.getBootId())
      .setDeviceSerial(device.getSerial())
      .build();
    Profiler.Device oldApiDevice = Profiler.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.KITKAT_WATCH).build();
    myRpcService.updateDevice(session, device, oldApiDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Check the message and the title have not changed.
    assertEquals(stage.getMessage(), NullMonitorStage.DEVICE_NOT_SUPPORTED_MESSAGE);
    assertEquals(stage.getTitle(), NullMonitorStage.DEVICE_NOT_SUPPORTED_TITLE);

    // Update the device to an API >= 21 and tick the timer to let it to be updated.
    Profiler.Device newApiDevice = Profiler.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.LOLLIPOP).build();
    myRpcService.updateDevice(session, oldApiDevice, newApiDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    // Device has valid API, but no debuggable processes.
    // Check the message and the title have changed to the appropriate ones.
    assertEquals(stage.getMessage(), NullMonitorStage.NO_DEBUGGABLE_PROCESS_MESSAGE);
    assertEquals(stage.getTitle(), NullMonitorStage.ANDROID_PROFILER_TITLE);
  }
}
