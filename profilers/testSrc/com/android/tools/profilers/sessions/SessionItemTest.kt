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
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.NullMonitorStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioMonitorStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.google.common.truth.Truth
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionItemTest {
  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)
  private val myMemoryService = FakeMemoryService()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionItemTestChannel",
    myTransportService,
    FakeProfilerService(myTimer),
    myMemoryService,
    FakeCpuService(),
    FakeEventService()
  )

  private val myIdeServices = FakeIdeProfilerServices().apply {
    enableEventsPipeline(true)
  }
  private val myProfilers by lazy { StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer) }

  @Test
  fun testNavigateToStudioMonitorStage() {
    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = myProfilers.sessionsManager
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build()
    sessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)

    // Navigate to a random stage, and make sure selecting the session item will go back to StudioMonitorStage.
    myProfilers.stage = MainMemoryProfilerStage(myProfilers)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)
  }

  @Test
  fun testAvoidRedundantNavigationToMonitorStage() {
    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = myProfilers.sessionsManager
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setPid(2).setState(Common.Process.State.ALIVE).build()
    sessionsManager.beginSession(device.deviceId, device, process)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(StudioMonitorStage::class.java)

    val stage = myProfilers.stage
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    Truth.assertThat(myProfilers.stage).isEqualTo(stage)
  }

  @Ignore("b/136292864")
  @Test
  fun testNonFullSessionNavigation() {
    Truth.assertThat(myProfilers.stageClass).isEqualTo(NullMonitorStage::class.java)

    val sessionsManager = myProfilers.sessionsManager
    val session = sessionsManager.createImportedSessionLegacy("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 1, 2, 0)
    sessionsManager.update()
    sessionsManager.setSession(session)
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)

    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    sessionItem.onSelect()
    // Selecting a memory capture session should not navigate to StudioMonitorStage
    Truth.assertThat(myProfilers.stageClass).isEqualTo(MainMemoryProfilerStage::class.java)
  }

  @Ignore("b/136292864")
  @Test
  fun testImportedHprofSessionName() {
    val sessionsManager = myProfilers.sessionsManager
    sessionsManager.createImportedSessionLegacy("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    sessionsManager.update()
    Truth.assertThat(sessionsManager.sessionArtifacts.size).isEqualTo(1)
    val sessionItem = sessionsManager.sessionArtifacts[0] as SessionItem
    Truth.assertThat(sessionItem.getSubtitle()).isEqualTo(SessionItem.SESSION_LOADING)

    val heapDumpInfo = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    sessionsManager.update()
    Truth.assertThat(sessionItem.getSubtitle()).isEqualTo("Heap Dump")
  }

  @Test
  fun testDurationUpdates() {
    var aspectChangeCount1 = 0
    val observer1 = AspectObserver()
    val finishedSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(TimeUnit.SECONDS.toNanos(10)).build()
    val finishedSessionItem = SessionItem(myProfilers, finishedSession,
                                          Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    finishedSessionItem.addDependency(observer1)
      .onChange(SessionItem.Aspect.MODEL) { aspectChangeCount1++ }
    Truth.assertThat(finishedSessionItem.getSubtitle()).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)
    // Updating should not affect finished sessions.
    finishedSessionItem.update(TimeUnit.SECONDS.toNanos(1))
    Truth.assertThat(finishedSessionItem.getSubtitle()).isEqualTo("5 sec")
    Truth.assertThat(aspectChangeCount1).isEqualTo(0)

    var aspectChangeCount2 = 0
    val observer2 = AspectObserver()
    val ongoingSession = Common.Session.newBuilder()
      .setStartTimestamp(TimeUnit.SECONDS.toNanos(5))
      .setEndTimestamp(Long.MAX_VALUE).build()
    val ongoingSessionItem = SessionItem(myProfilers, ongoingSession,
                                         Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    ongoingSessionItem.addDependency(observer2)
      .onChange(SessionItem.Aspect.MODEL) { aspectChangeCount2++ }
    Truth.assertThat(ongoingSessionItem.getSubtitle()).isEqualTo("0 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(0)
    ongoingSessionItem.update(TimeUnit.SECONDS.toNanos(2))
    Truth.assertThat(ongoingSessionItem.getSubtitle()).isEqualTo("2 sec")
    Truth.assertThat(aspectChangeCount2).isEqualTo(1)
  }

  @Test
  fun `get session name with valid metadata`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.FULL
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("app (Pixel 3A XL)")
  }

  @Test
  fun `get session name uses raw metadata name when parsing fails`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app"  // the name should have the device name at the end, therefore this is invalid
      type = Common.SessionMetaData.SessionType.FULL
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("com.google.app")
  }

  @Test
  fun `get session name uses raw metadata when session type is not FULL`() {
    val session = Common.Session.newBuilder().build()

    val sessionItem = SessionItem(myProfilers, session, Common.SessionMetaData.newBuilder().apply {
      sessionName = "com.google.app (Pixel 3A XL)"
      type = Common.SessionMetaData.SessionType.UNSPECIFIED
    }.build())

    Truth.assertThat(sessionItem.name).isEqualTo("com.google.app (Pixel 3A XL)")
  }
}