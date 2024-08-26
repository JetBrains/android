/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.leakcanary

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.ProfilerTaskType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LeakCanarySessionArtifactTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakCanarySessionArtifactTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private val timeStamp1 = System.nanoTime().minus(3000000)
  private val timeStamp2 = System.nanoTime().minus(2000000)
  private val timeStamp3 = System.nanoTime().minus(1000000)
  private val timeStamp4 = System.nanoTime().minus(900000)
  private val timeStamp5 = System.nanoTime().minus(800000)
  private lateinit var leakCanarySessionArtifact: LeakCanarySessionArtifact

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    stage = LeakCanaryModel(profilers)
    profilers.stage = stage
    val mockSession = Common.Session.newBuilder().setStartTimestamp(timeStamp1).build()
    val mockSessionMetadata = SessionMetaData.newBuilder().build()
    leakCanarySessionArtifact = LeakCanarySessionArtifact(profilers, mockSession, mockSessionMetadata)
  }

  @Test
  fun `artifactProto - check for LeakCanaryLogcatData instance`() {
      assertNotNull(leakCanarySessionArtifact.artifactProto)
  }

  @Test
  fun `name - LeakCanary View`() {
    assertEquals("LeakCanary", leakCanarySessionArtifact.name)
  }

  @Test
  fun `timestampNs - match session start timestamp`() {
    assertEquals(timeStamp1, leakCanarySessionArtifact.timestampNs)
  }

  @Test
  fun `isOngoing - session is not ended`() {
    assertTrue(leakCanarySessionArtifact.isOngoing)
  }

  @Test
  fun `canExport - LeakCanary can't be exported`() {
    assertFalse(leakCanarySessionArtifact.canExport)
  }

  @Test
  fun `doSelect - LeakEvents from past`() {
    addEventsForLeakCanaryLogCat()
    val mockSession = Common.Session.newBuilder()
      .setStartTimestamp(timeStamp1)
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setSessionId(FakeTransportService.FAKE_PROCESS.pid.toLong())
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setEndTimestamp(timeStamp3)
      .build()
    val mockSessionMetadata = SessionMetaData.newBuilder().setType(SessionMetaData.SessionType.FULL).build()
    val sessionItem = SessionItem(profilers, mockSession, mockSessionMetadata)
    val leakCanaryTaskHandler = LeakCanaryTaskHandler(profilers.sessionsManager)
    profilers.addTaskHandler(ProfilerTaskType.LEAKCANARY, leakCanaryTaskHandler)
    profilers.sessionsManager.currentTaskType = ProfilerTaskType.LEAKCANARY
    profilers.sessionsManager.mySessionItems[mockSession.sessionId] = sessionItem
    profilers.sessionsManager.mySessionMetaDatas[mockSession.sessionId] = mockSessionMetadata
    val leakCanarySessionArtifact = LeakCanarySessionArtifact(profilers, mockSession, mockSessionMetadata)
    leakCanarySessionArtifact.doSelect()
    assertTrue { profilers.stage is LeakCanaryModel }
    assertEquals(3, (profilers.stage as LeakCanaryModel).leaks.value.size)
  }

  @Test
  fun `doSelect - no LeakEvents from past`() {
    addEventsForLeakCanaryLogCat()
    val mockSession = Common.Session.newBuilder()
      .setStartTimestamp(timeStamp5)
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setSessionId(FakeTransportService.FAKE_PROCESS.pid.toLong())
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setEndTimestamp(timeStamp3)
      .build()
    val mockSessionMetadata = SessionMetaData.newBuilder().setType(SessionMetaData.SessionType.FULL).build()
    val sessionItem = SessionItem(profilers, mockSession, mockSessionMetadata)
    val leakCanaryTaskHandler = LeakCanaryTaskHandler(profilers.sessionsManager)
    profilers.addTaskHandler(ProfilerTaskType.LEAKCANARY, leakCanaryTaskHandler)
    profilers.sessionsManager.currentTaskType = ProfilerTaskType.LEAKCANARY
    profilers.sessionsManager.mySessionItems[mockSession.sessionId] = sessionItem
    profilers.sessionsManager.mySessionMetaDatas[mockSession.sessionId] = mockSessionMetadata
    val leakCanarySessionArtifact = LeakCanarySessionArtifact(profilers, mockSession, mockSessionMetadata)
    leakCanarySessionArtifact.doSelect()
    assertTrue { profilers.stage is LeakCanaryModel }
    assertEquals(0, (profilers.stage as LeakCanaryModel).leaks.value.size)
  }

  @Test
  fun `getSessionArtifacts - LeakEvents present for session range`() {
    addEventsForLeakCanaryLogCat()
    val mockSession = Common.Session.newBuilder()
      .setStartTimestamp(timeStamp1)
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setEndTimestamp(timeStamp3)
      .build()
    val mockSessionMetadata = SessionMetaData.newBuilder().build()
    val result = LeakCanarySessionArtifact.getSessionArtifacts(profilers, mockSession, mockSessionMetadata)
    assertEquals(1, result.size)
  }

  @Test
  fun `getSessionArtifacts - LeakEvents not present for session range`() {
    addEventsForLeakCanaryLogCat()
    val mockSession = Common.Session.newBuilder()
      .setStartTimestamp(timeStamp4)
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setEndTimestamp(timeStamp5)
      .build()
    val mockSessionMetadata = SessionMetaData.newBuilder().build()
    val result = LeakCanarySessionArtifact.getSessionArtifacts(profilers, mockSession, mockSessionMetadata)
    assertEquals(0, result.size)
  }

  private fun addEventsForLeakCanaryLogCat() {

    val file = TestUtils.resolveWorkspacePath("${FakeLeakCanaryCommandHandler.TEST_DATA_PATH}/SingleApplicationLeak.txt").toFile()
    val fileContent = file.readText()

    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setPid(FakeTransportService.FAKE_PROCESS.pid)
                                        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
                                        .setIsEnded(true)
                                        .setTimestamp(timeStamp1)
                                        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData
                                                               .newBuilder()
                                                               .setLogcatMessage(fileContent).build())
                                        .build())

    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setPid(FakeTransportService.FAKE_PROCESS.pid)
                                        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
                                        .setIsEnded(true)
                                        .setTimestamp(timeStamp2)
                                        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData
                                                               .newBuilder()
                                                               .setLogcatMessage(fileContent).build())
                                        .build())

    transportService.addEventToStream(FakeTransportService.FAKE_DEVICE_ID,
                                      Common.Event.newBuilder()
                                        .setPid(FakeTransportService.FAKE_PROCESS.pid)
                                        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
                                        .setIsEnded(true)
                                        .setTimestamp(timeStamp3)
                                        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData
                                                               .newBuilder()
                                                               .setLogcatMessage(fileContent).build())
                                        .build())
  }
}