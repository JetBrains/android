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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.leakcanary.LeakCanaryTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.google.common.truth.Truth
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LeakCanaryTaskHandlerTest: WithFakeTimer {

  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakCanaryTaskHandlerTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var leakCanaryTaskHandler: LeakCanaryTaskHandler

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    val mockSession = Common.Session.newBuilder()
      .setStartTimestamp(System.currentTimeMillis())
      .setPid(FakeTransportService.FAKE_PROCESS.pid)
      .setSessionId(FakeTransportService.FAKE_PROCESS.pid.toLong())
      .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
      .setEndTimestamp(System.currentTimeMillis() + 10000000)
      .build()
    val mockSessionMetadata = Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build()
    val sessionItem = SessionItem(profilers, mockSession, mockSessionMetadata)
    leakCanaryTaskHandler = LeakCanaryTaskHandler(profilers.sessionsManager)
    profilers.addTaskHandler(ProfilerTaskType.LEAKCANARY, leakCanaryTaskHandler)
    profilers.sessionsManager.currentTaskType = ProfilerTaskType.LEAKCANARY
    profilers.sessionsManager.mySessionItems[mockSession.sessionId] = sessionItem
    profilers.sessionsManager.mySessionMetaDatas[mockSession.sessionId] = mockSessionMetadata
    profilers.sessionsManager.setSession(mockSession)
  }

  @Test
  fun `setUpStage - check stage is not null and its of LeakCanaryModel type`() {
    leakCanaryTaskHandler.setupStage()
    assertNotNull(leakCanaryTaskHandler.stage)
    assertNotNull(leakCanaryTaskHandler.stage as LeakCanaryModel)
    // By default, no leaks exist
    assertEquals(0, (leakCanaryTaskHandler.stage as LeakCanaryModel).leaks.value.size)
    assertSame(leakCanaryTaskHandler.stage as LeakCanaryModel, profilers.stage as LeakCanaryModel)
  }

  @Test
  fun `startCapture and stopCapture - starting LeakCanary and fetching events till stop`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                      FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                        "SingleApplicationLeak.txt",
                                        "SingleApplicationLeakAnalyzeCmd.txt",
                                        "MultiApplicationLeak.txt",
                                        "NoLeak.txt"
                                      ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    leakCanaryTaskHandler.setupStage()
    val stage = leakCanaryTaskHandler.stage as LeakCanaryModel
    leakCanaryTaskHandler.startTask(LeakCanaryTaskArgs(false, null))
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leak events
    assertEquals(4, stage.leaks.value.size) // 4 events are sent
    leakCanaryTaskHandler.stopTask()
    // After stage exit we get all events
    assertEquals(4, stage.leaks.value.size) // 4 events are sent

    val infoEvents = LeakCanaryModel.getLeakCanaryLogcatInfo(profilers.client, profilers.session, Range(Long.MIN_VALUE.toDouble(),
                                                                                                    Long.MAX_VALUE.toDouble()))
    assertEquals(1, infoEvents.size)
    assertEquals(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO, infoEvents[0].kind)
  }

  @Test
  fun `startCapture, stopCapture and load test - send events stop and reload the recording`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt",
                                         "SingleApplicationLeakAnalyzeCmd.txt",
                                         "MultiApplicationLeak.txt",
                                         "NoLeak.txt"
                                       ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    leakCanaryTaskHandler.setupStage()
    val stage = leakCanaryTaskHandler.stage as LeakCanaryModel
    leakCanaryTaskHandler.startTask(LeakCanaryTaskArgs(false, null))
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    leakCanaryTaskHandler.stopTask()
    // After stage exit we get all events
    assertEquals(4, stage.leaks.value.size) // 4 events are sent

    // Verify one info event is there
    val infoEvents = LeakCanaryModel.getLeakCanaryLogcatInfo(profilers.client, profilers.session,
                                                             Range(profilers.session.startTimestamp.toDouble(),
                                                             profilers.session.endTimestamp.toDouble()))

    assertEquals(1, infoEvents.size)
    assertEquals(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO, infoEvents[0].kind)

    stage.clearLeaks()
    assertEquals(0, stage.leaks.value.size) // 0 events are clear

    val leakCanarySessionArtifact =
      LeakCanarySessionArtifact.getSessionArtifacts(profilers, profilers.session, Common.SessionMetaData.getDefaultInstance())

    assertEquals(1, leakCanarySessionArtifact.size)
    val result = leakCanaryTaskHandler
      .loadTask(LeakCanaryTaskArgs(false, leakCanarySessionArtifact[0] as LeakCanarySessionArtifact))

    assertTrue(result)
    assertEquals(4, stage.leaks.value.size) // 4 leaks after load
  }

  @Test
  fun `checkDeviceAndProcess - always returns true`() {
    val debuggableProcess = TaskHandlerTestUtils.createProcess(isProfileable = false)
    val qDevice = TaskHandlerTestUtils.createDevice(AndroidVersion.VersionCodes.Q)
    assertNull(leakCanaryTaskHandler.checkSupportForDeviceAndProcess(qDevice, debuggableProcess))
  }

  @Test
  fun `testArgs - session ended`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to SessionArtifactUtils.createSessionItem(profilers, selectedSession, 1,
                                                   listOf(SessionArtifactUtils.createLeakCanarySessionArtifact(profilers, selectedSession,
                                                                                                               LeakCanary
                                                                                                                 .LeakCanaryLogcatInfo
                                                                                                                 .getDefaultInstance()))))
    val leakCanaryTaskHandlerTaskArgs = leakCanaryTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs).isNotNull()
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs).isInstanceOf(LeakCanaryTaskArgs::class.java)
    leakCanaryTaskHandlerTaskArgs as LeakCanaryTaskArgs
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs.getLeakCanaryArtifact()).isNotNull()
  }

  @Test
  fun `testArgs - session ongoing`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(Long.MAX_VALUE).build()
    val sessionIdToSessionItems = mapOf(
      1L to SessionArtifactUtils.createSessionItem(profilers, selectedSession, 1,
                                                   listOf(SessionArtifactUtils.createLeakCanarySessionArtifact(profilers, selectedSession,
                                                                                                               LeakCanary
                                                                                                                 .LeakCanaryLogcatInfo
                                                                                                                 .getDefaultInstance()))))
    val leakCanaryTaskHandlerTaskArgs = leakCanaryTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs).isNotNull()
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs).isInstanceOf(LeakCanaryTaskArgs::class.java)
    leakCanaryTaskHandlerTaskArgs as LeakCanaryTaskArgs
    // For start task, artifact is not set
    Truth.assertThat(leakCanaryTaskHandlerTaskArgs.getLeakCanaryArtifact()).isNull()
  }

  @Test
  fun `testTaskName`() {
    assertEquals("LeakCanary",leakCanaryTaskHandler.getTaskName())
  }

  @Test(expected = Throwable::class)
  fun `loadTaskTest - Not LeakCanaryArgs type`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(Long.MAX_VALUE-10).build()
    val result = leakCanaryTaskHandler
      .loadTask(CpuTaskArgs(false,
                            SessionArtifactUtils.createCpuCaptureSessionArtifact(profilers, selectedSession, 1, 10)))
    assertFalse(result)
  }

  @Test(expected = Throwable::class)
  fun `loadTaskTest - no task args`() {
    val result = leakCanaryTaskHandler.loadTask(CpuTaskArgs(false, null))
    assertFalse(result)
  }

  @Test
  fun `loadTaskTest - LeakCanaryArgs type`() {
    val result = leakCanaryTaskHandler
      .loadTask(LeakCanaryTaskArgs(false,  SessionArtifactUtils.createLeakCanarySessionArtifact(profilers, profilers.session,
                                                                                                LeakCanary.LeakCanaryLogcatInfo
                                                                                                  .getDefaultInstance())))
    assertTrue(result)
  }
}