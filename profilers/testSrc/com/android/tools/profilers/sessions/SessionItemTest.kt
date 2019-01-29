/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.*
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.MemoryProfilerStage
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionItemTest {
  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
      "SessionItemTestChannel",
      myProfilerService,
      myMemoryService,
      FakeCpuService(),
      FakeEventService(),
      FakeNetworkService.newBuilder().build()
  )

  @Test
  fun testNavigateToStudioMonitorStage() {
    val profilers = StudioProfilers(
        myGrpcChannel.client,
        FakeIdeProfilerServices(),
        FakeTimer()
    )
    Truth.assertThat(profilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = profilers.sessionsManager
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build()
    sessionsManager.beginSession(device, process)
    Truth.assertThat(profilers.stageClass).isEqualTo(StudioMonitorStage::class.java)

    // Navigate to a random stage, and make sure selecting the session item will go back to StudioMonitorStage.
    profilers.stage = MemoryProfilerStage(profilers)
    Truth.assertThat(profilers.stageClass).isEqualTo(MemoryProfilerStage::class.java)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    Truth.assertThat(profilers.stageClass).isEqualTo(StudioMonitorStage::class.java)
  }

  @Test
  fun testNonFullSessionNavigation() {
    val profilers = StudioProfilers(
      myGrpcChannel.client,
      FakeIdeProfilerServices(),
      FakeTimer()
    )
    Truth.assertThat(profilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = profilers.sessionsManager
    val session = sessionsManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    sessionsManager.update()
    sessionsManager.setSession(session)
    Truth.assertThat(profilers.stageClass).isEqualTo(MemoryProfilerStage::class.java)

    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    // Selecting a memory capture session should not navigate to StudioMonitorStage
    Truth.assertThat(profilers.stageClass).isEqualTo(MemoryProfilerStage::class.java)
  }

  @Test
  fun testImportedHprofSessionName() {
    val profilers = StudioProfilers(myGrpcChannel.client, FakeIdeProfilerServices(), FakeTimer())
    val sessionsManager = profilers.sessionsManager
    sessionsManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    sessionsManager.update()
    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    Truth.assertThat(sessionItem.subtitle).isEqualTo(SessionItem.SESSION_LOADING)

    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    sessionsManager.update()
    Truth.assertThat(sessionItem.subtitle).isEqualTo("Heap Dump")
  }

  @Test
  fun testDurationUpdates() {
    val profilers = StudioProfilers(
      myGrpcChannel.client,
      FakeIdeProfilerServices(),
      FakeTimer()
    )

    var aspectChangeCount1 = 0
    val observer1 = AspectObserver()
    val finishedSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(TimeUnit.SECONDS.toNanos(10)).build()
    val finishedSessionItem = SessionItem(profilers, finishedSession,
                                          Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    finishedSessionItem.addDependency(observer1)
      .onChange(SessionItem.Aspect.MODEL, { aspectChangeCount1++ })
    Truth.assertThat(finishedSessionItem.subtitle).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)
    // Updating should not affect finished sessions.
    finishedSessionItem.update(TimeUnit.SECONDS.toNanos(1))
    Truth.assertThat(finishedSessionItem.subtitle).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)

    var aspectChangeCount2 = 0
    val observer2 = AspectObserver()
    val ongoingSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(Long.MAX_VALUE).build()
    val ongoingSessionItem = SessionItem(profilers, ongoingSession,
                                         Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    ongoingSessionItem.addDependency(observer2)
      .onChange(SessionItem.Aspect.MODEL, { aspectChangeCount2++ })
    Truth.assertThat(ongoingSessionItem.subtitle).isEqualTo("0 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(0)
    ongoingSessionItem.update(TimeUnit.SECONDS.toNanos(2))
    Truth.assertThat(ongoingSessionItem.subtitle).isEqualTo("2 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(1)
  }
}