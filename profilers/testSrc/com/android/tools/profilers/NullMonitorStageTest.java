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
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NullMonitorStageTest {

  private FakeProfilerService myRpcService = new FakeProfilerService(false);

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("NullMonitorStageTest", myRpcService);

  @Test
  public void testModelType() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    NullMonitorStage stage = new NullMonitorStage(profilers);
    assertEquals(stage.getType(), NullMonitorStage.Type.NO_DEVICE);

    // Add a device
    Common.Device device = Common.Device.getDefaultInstance();
    myRpcService.addDevice(device);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(stage.getType(), NullMonitorStage.Type.UNSUPPORTED_DEVICE);

    // Update the device to an API < 21
    Common.Device oldApiDevice = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.KITKAT_WATCH).build();
    myRpcService.updateDevice(device, oldApiDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(stage.getType(), NullMonitorStage.Type.UNSUPPORTED_DEVICE);

    // Update the device to an API >= 21 and tick the timer to let it to be updated.
    Common.Device newApiDevice = Common.Device.newBuilder().setFeatureLevel(AndroidVersion.VersionCodes.LOLLIPOP).build();
    myRpcService.updateDevice(oldApiDevice, newApiDevice);
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertEquals(stage.getType(), NullMonitorStage.Type.NO_DEBUGGABLE_PROCESS);
  }
}
