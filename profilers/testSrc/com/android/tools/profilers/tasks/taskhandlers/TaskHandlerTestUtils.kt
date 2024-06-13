/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.tasks.taskhandlers

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.SupportLevel
import com.google.common.truth.Truth

object TaskHandlerTestUtils {
  fun startSession(exposureLevel: Common.Process.ExposureLevel,
                   profilers: StudioProfilers,
                   transportService: FakeTransportService,
                   timer: FakeTimer,
                   taskType: Common.ProfilerTaskType) {
   startSession(exposureLevel, AndroidVersion.VersionCodes.Q, profilers, transportService, timer, taskType)
  }

  fun startSession(exposureLevel: Common.Process.ExposureLevel,
                   deviceFeatureLevel: Int,
                   profilers: StudioProfilers,
                   transportService: FakeTransportService,
                   timer: FakeTimer,
                   taskType: Common.ProfilerTaskType) {
    // The following creates and starts a fake debuggable session so that features that requires a debuggable process are supported such as
    // heap dump and java/kotlin allocations.
    profilers.setPreferredProcess(null, FakeTransportService.FAKE_PROCESS.name, null)
    // To support the Native Allocation tracing feature, the feature level of the device must be >= Q.
    val device = FakeTransportService.FAKE_DEVICE.toBuilder().setApiLevel(deviceFeatureLevel).setFeatureLevel(deviceFeatureLevel).build()
    transportService.addDevice(device)
    val debuggableEvent = FakeTransportService.FAKE_PROCESS.toBuilder()
      .setStartTimestampNs(5)
      .setExposureLevel(exposureLevel)
      .build()
    transportService.addProcess(device, debuggableEvent)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS) // Wait for the session to auto start and select.
    profilers.setProcess(device, null, taskType, false) // Will start a new session on the preferred process
    timer.tick(FakeTimer.ONE_SECOND_IN_NS) // Wait for the session to auto start and select.
    Truth.assertThat(profilers.session.pid).isEqualTo(FakeTransportService.FAKE_PROCESS.pid)
    Truth.assertThat(
      profilers.selectedSessionSupportLevel == SupportLevel.DEBUGGABLE ||
      profilers.selectedSessionSupportLevel == SupportLevel.PROFILEABLE).isTrue()
    if (exposureLevel == Common.Process.ExposureLevel.DEBUGGABLE) {
      Truth.assertThat(profilers.selectedSessionSupportLevel).isEqualTo(SupportLevel.DEBUGGABLE)
    }
    else if (exposureLevel == Common.Process.ExposureLevel.PROFILEABLE) {
      Truth.assertThat(profilers.selectedSessionSupportLevel).isEqualTo(SupportLevel.PROFILEABLE)
    }
  }

  fun createDevice(versionCode: Int): Common.Device = Common.Device.newBuilder().setFeatureLevel(versionCode).build()

  fun createProcess(isProfileable: Boolean): Common.Process = Common.Process.newBuilder().setExposureLevel(
    if (isProfileable) Common.Process.ExposureLevel.PROFILEABLE else Common.Process.ExposureLevel.DEBUGGABLE).build()
}