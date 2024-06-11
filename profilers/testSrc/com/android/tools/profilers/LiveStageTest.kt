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
package com.android.tools.profilers

import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.cpu.LiveCpuUsageModel
import com.android.tools.profilers.event.EventMonitor
import com.android.tools.profilers.memory.LiveMemoryFootprintModel
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito
import kotlin.test.assertFalse

class LiveStageTest {
  private lateinit var myProfilers: StudioProfilers
  private lateinit var myLiveStage: LiveStage

  @Before
  fun setUp() {
    myProfilers = Mockito.mock(StudioProfilers::class.java, Mockito.RETURNS_DEEP_STUBS)
    Mockito.`when`(myProfilers.selectedSessionSupportLevel).thenReturn(SupportLevel.DEBUGGABLE)
    myLiveStage = LiveStage(myProfilers)
  }

  @Test
  fun testEnter() {
    myLiveStage.enter()
    val result = myLiveStage.liveModels
    assertThat(result.size).isEqualTo(2)
    // 1st component is Cpu
    assertThat(result[0]).isInstanceOf(LiveCpuUsageModel::class.java)
    // 2nd component is Memory
    assertThat(result[1]).isInstanceOf(LiveMemoryFootprintModel::class.java)
  }

  @Test
  fun testStopDisableAutoProfiling() {
    myLiveStage.exit()
    assertThat( myLiveStage.studioProfilers.autoProfilingEnabled).isFalse()
  }

  @Test
  fun testExitModel() {
    myLiveStage.exit()
    val result = myLiveStage.liveModels
    // All live models to be cleared on exit
    assertThat(result.size).isEqualTo(0)
  }

  @Test
  fun testStageType() {
    val result = myLiveStage.stageType
    assertThat(result).isEqualTo(AndroidProfilerEvent.Stage.LIVE_STAGE);
  }

  @Test
  fun testExitSession() {
    myLiveStage.enter()
    startSessionHelper()
    myLiveStage.exit()
    assertFalse { myLiveStage.studioProfilers.sessionsManager.isSessionAlive }
  }

  private fun startSessionHelper() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Utils.debuggableProcess { pid = 10 }
    myLiveStage.studioProfilers.sessionsManager.beginSession(device.deviceId, device, process)
    myLiveStage.studioProfilers.sessionsManager.update()
  }

  @Test
  fun testEventMonitorDebuggablePostO() {
    val device = Common.Device.newBuilder().setFeatureLevel(26).build()
    val stream = Common.Stream.newBuilder().setStreamId(123).setDevice(device).setType(Common.Stream.Type.DEVICE).build()
    Mockito.`when`(myProfilers.device).thenReturn(device)
    Mockito.`when`(myProfilers.getStream(anyLong())).thenReturn(stream)

    myLiveStage.enter()
    val result = myLiveStage.eventMonitor
    assertThat(result.isPresent).isTrue()
    assertThat(result.get()).isInstanceOf(EventMonitor::class.java)
  }

  @Test
  fun testEventMonitorDebuggablePreO() {
    val device = Common.Device.newBuilder().setFeatureLevel(25).build()
    Mockito.`when`(myProfilers.device).thenReturn(device)
    myLiveStage.enter()
    val result = myLiveStage.eventMonitor
    assertThat(result.isPresent).isFalse()
  }

  @Test
  fun testEventMonitorNotDebuggable() {
    Mockito.`when`(myProfilers.selectedSessionSupportLevel).thenReturn(SupportLevel.PROFILEABLE)
    myLiveStage.enter()
    myLiveStage = LiveStage(myProfilers)
    val result = myLiveStage.eventMonitor
    assertThat(result.isPresent).isFalse()
  }
}