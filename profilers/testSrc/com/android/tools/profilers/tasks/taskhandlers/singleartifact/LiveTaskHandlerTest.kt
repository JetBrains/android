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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact

import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.LiveStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils.createHeapProfdSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createHprofSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createSessionItem
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.LiveTaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.HeapDumpTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandlerFactory
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class LiveTaskHandlerTest {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false, ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("LiveTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var liveTaskHandler: LiveTaskHandler

  @Before
  fun setup() {
    ideProfilerServices.enableTaskBasedUx(true)
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    liveTaskHandler = LiveTaskHandler(myManager)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())

    setupTaskHandlers()
  }

  private fun setupTaskHandlers() {
    val taskHandlers = ProfilerTaskHandlerFactory.createTaskHandlers(myProfilers.sessionsManager)
    taskHandlers.forEach { (type, handler) -> myProfilers.addTaskHandler(type, handler) }
  }

  @Test
  fun testGetTaskName() {
    assertThat(liveTaskHandler.getTaskName()).isEqualTo("Live View")
  }

  @Test
  fun testCheckDeviceAndProcess() {
    val debuggableProcess = TaskHandlerTestUtils.createProcess(isProfileable = false)
    val qDevice = TaskHandlerTestUtils.createDevice(AndroidVersion.VersionCodes.Q)
    val pDevice = TaskHandlerTestUtils.createDevice(AndroidVersion.VersionCodes.P)
    assertThat(liveTaskHandler.supportsDeviceAndProcess(qDevice, debuggableProcess)).isTrue()
    assertThat(liveTaskHandler.supportsDeviceAndProcess(pDevice, debuggableProcess)).isTrue()

    val profileableProcess = TaskHandlerTestUtils.createProcess(isProfileable = true)
    assertThat(liveTaskHandler.supportsDeviceAndProcess(qDevice, profileableProcess)).isTrue()
    assertThat(liveTaskHandler.supportsDeviceAndProcess(pDevice, profileableProcess)).isTrue()
  }

  @Test
  fun `test startTask called on enter`() {
    val liveViewHandlerSpy = Mockito.spy(liveTaskHandler)
    val mockArgs = MockitoKt.mock<TaskArgs>()
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.LIVE_VIEW)
    liveViewHandlerSpy.enter(mockArgs)

    // Verify that the setupStage method is only invoked once on enter.
    Mockito.verify(liveViewHandlerSpy, Mockito.times(1)).startTask(mockArgs)
  }


  @Test
  fun `test enter stage sets the Stage to LiveView`() {
    val mockArgs = MockitoKt.mock<TaskArgs>()
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.LIVE_VIEW)
    liveTaskHandler.enter(mockArgs)

    // Enter stage sets the stage to LiveView stage.
    assertThat(myProfilers.stage).isInstanceOf(LiveStage::class.java)
  }

  @Test(expected = Throwable::class)
  fun `test enter stage throw error when the task type is not mentioned in the session`() {
    val mockArgs = MockitoKt.mock<TaskArgs>()
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.UNSPECIFIED_TASK)
    liveTaskHandler.enter(mockArgs)
  }

  @Test
  fun `test startTask calling the enter method of LiveStage`() {
    liveTaskHandler.startTask(LiveTaskArgs(false, null))
    val liveStage = myProfilers.stage as LiveStage

    // Should have Cpu and Memory live data models
    assertThat(liveStage.liveModels.size).isEqualTo(2)
  }

  @Test
  fun `test supportsArtifact when its SessionItem`() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    assertThat(liveTaskHandler.supportsArtifact(createSessionItem(myProfilers, selectedSession, 1, listOf()))).isTrue()
  }

  @Test
  fun `test supportsArtifact when its artifact`() {
    val heapProfdSessionArtifact = createHeapProfdSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    assertThat(liveTaskHandler.supportsArtifact(heapProfdSessionArtifact)).isFalse()
  }

  @Test
  fun testCreateArgsSuccessfully() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf()),
    )
    // Begin live view session
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.LIVE_VIEW)

    val liveTaskHandlerCreateArgs = liveTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(liveTaskHandlerCreateArgs).isNotNull()
    assertThat(liveTaskHandlerCreateArgs).isInstanceOf(LiveTaskArgs::class.java)
    assertThat((liveTaskHandlerCreateArgs as LiveTaskArgs).getLiveTaskArtifact()).isEqualTo(
      sessionIdToSessionItems[selectedSession.sessionId])
  }

  @Test
  fun testCreateArgsFailsToFindArtifactDueToMismatchedSessionIds() {
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf()),
    )
    // Begin live view session
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.LIVE_VIEW)
    assertThrows(IllegalStateException::class.java) {
      liveTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testCreateArgsFailsToFindArtifactDueToNotLiveViewTaskType() {
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf()),
    )
    // Begin non-live view session
    TaskHandlerTestUtils.startSession(Common.Process.ExposureLevel.DEBUGGABLE,
                                      myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.JAVA_KOTLIN_METHOD_RECORDING)

    assertThrows(IllegalStateException::class.java) {
      liveTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testLoadArgsWithNonLiveArgs() {
    val heapProfdSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val result = liveTaskHandler.loadTask(HeapDumpTaskArgs(artifact = heapProfdSessionArtifact))
    assertThat(result).isFalse()
  }

  @Test
  fun testLoadArgsWithValidArgs() {
    val finishedSession = Common.Session.getDefaultInstance()
    val sessionItem = SessionItem(myProfilers, finishedSession,
                                  Common.SessionMetaData.newBuilder().setType(Common.SessionMetaData.SessionType.FULL).build())
    val result = liveTaskHandler.loadTask(LiveTaskArgs(artifact = sessionItem))
    assertThat(result).isTrue()
    assertThat(myProfilers.stage).isInstanceOf(LiveStage::class.java)
  }

  @Test
  fun testLoadArgsWithInvalidArgs() {
    val selectedSession = Common.Session.getDefaultInstance()
    val sessionItem = createSessionItem(myProfilers, selectedSession, 1, listOf())
    val result = liveTaskHandler.loadTask(LiveTaskArgs(artifact = sessionItem))
    assertThat(result).isTrue()

    // Since SessionMetaData.SessionType.FULL is not true, the stage is not changed hence LiveStage is not set.
    assertThat(myProfilers.stage).isNotInstanceOf(LiveStage::class.java)
  }
}