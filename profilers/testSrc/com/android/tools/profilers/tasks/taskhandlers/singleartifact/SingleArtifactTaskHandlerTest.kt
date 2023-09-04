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

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.InterimStage
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.sessions.SessionArtifact
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.TaskArgs
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.SingleArtifactTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings

class SingleArtifactTaskHandlerTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer, false)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("SingleArtifactTaskHandlerTestChannel", myTransportService)

  private val myIdeServices = FakeIdeProfilerServices()
  private val myProfilers by lazy { StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeServices, myTimer) }

  /**
   * Constructs and returns a mock {@link SingleArtifactTaskHandler} using a mocked {@link SessionsManager}.
   *
   * Configured to call the real SingleArtifactTaskHandler#enter and SingleArtifactTaskHandler#isTaskNewlyFinished methods if invoked via
   * the mock.
   */
  private fun createMockSingleArtifactTaskHandler(): SingleArtifactTaskHandler<InterimStage> {
    val mockSessionsManager = MockitoKt.mock<SessionsManager>()
    val mockSingleArtifactTaskHandler = MockitoKt.mock<SingleArtifactTaskHandler<InterimStage>>(
      withSettings().useConstructor(mockSessionsManager)).apply {
      MockitoKt.whenever(enter(any())).thenCallRealMethod()
      MockitoKt.whenever(isTaskNewlyFinished(any(), any(), any())).thenCallRealMethod()
    }
    return mockSingleArtifactTaskHandler
  }

  @Test
  fun `test setupStage called on enter`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val mockArgs = MockitoKt.mock<TaskArgs>()
    mockSingleArtifactTaskHandler.enter(mockArgs)
    // Verify that the setupStage method is only invoked once on enter.
    verify(mockSingleArtifactTaskHandler, times(1)).setupStage()
  }

  @Test
  fun `test isTaskNewlyFinished with valid task type and terminated artifact in alive session`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val artifacts = listOf(createSessionArtifact(1L, false, Long.MAX_VALUE))
    val isTaskNewlyFinished = mockSingleArtifactTaskHandler.isTaskNewlyFinished(ProfilerTaskType.SYSTEM_TRACE, artifacts,
                                                                                mapOf(1L to ProfilerTaskType.SYSTEM_TRACE))
    assertThat(isTaskNewlyFinished).isTrue()
  }

  @Test
  fun `test isTaskNewlyFinished with empty artifacts`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val artifacts = listOf<SessionArtifact<*>>()
    // If the artifacts are empty, which is possible if the isTaskNewlyFinished method is called on a timer with constantly updated session
    // artifacts, it should return false to prevent any actions that would be triggered by a task being done.
    val isTaskNewlyFinished = mockSingleArtifactTaskHandler.isTaskNewlyFinished(ProfilerTaskType.SYSTEM_TRACE, artifacts,
                                                                                mapOf(1L to ProfilerTaskType.SYSTEM_TRACE))
    assertThat(isTaskNewlyFinished).isFalse()
  }

  @Test
  fun `test isTaskNewlyFinished with unspecified task type`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val artifacts = listOf(createSessionArtifact(1L, false, Long.MAX_VALUE))
    // Although the expected task type and the selected session's task type match, it is unspecified, so it should return false to
    // prevent any actions that would be triggered by a task being done.
    val isTaskNewlyFinished = mockSingleArtifactTaskHandler.isTaskNewlyFinished(ProfilerTaskType.UNSPECIFIED, artifacts,
                                                                                mapOf(1L to ProfilerTaskType.UNSPECIFIED))
    assertThat(isTaskNewlyFinished).isFalse()
  }

  @Test
  fun `test isTaskNewlyFinished with multiple terminated artifacts in alive sessions`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val artifacts = listOf(createSessionArtifact(1L, false, Long.MAX_VALUE), createSessionArtifact(1L, false, Long.MAX_VALUE))
    // Despite there being terminated artifacts under alive sessions (with the correct expected task type), there are more than one, so
    // which is not a valid state to be in.
    val exception = assertThrows(IllegalStateException::class.java) {
      mockSingleArtifactTaskHandler.isTaskNewlyFinished(ProfilerTaskType.SYSTEM_TRACE, artifacts,
                                                        mapOf(1L to ProfilerTaskType.SYSTEM_TRACE))
    }
    // Verify the IllegalStageException message is correct.
    assertThat(exception.message).isEqualTo(
      "There cannot be more than one terminated artifact under the currently alive session for a SingleArtifactTaskHandler.")
  }

  @Test
  fun `test isTaskNewlyFinished with valid task type and terminated artifact in terminated session`() {
    val mockSingleArtifactTaskHandler = createMockSingleArtifactTaskHandler()
    val artifacts = listOf(createSessionArtifact(1L, false, 100L))
    val isTaskNewlyFinished = mockSingleArtifactTaskHandler.isTaskNewlyFinished(ProfilerTaskType.SYSTEM_TRACE, artifacts,
                                                                                mapOf(1L to ProfilerTaskType.SYSTEM_TRACE))
    // Being a terminated artifact is not sufficient to indicate that the task is done for a SingleArtifactTaskHandler, as the terminated
    // artifact must be a child of an ongoing session as well.
    assertThat(isTaskNewlyFinished).isFalse()
  }

  private fun createSessionArtifact(sessionId: Long, isOngoing: Boolean, sessionEndTimestamp: Long): SessionArtifact<*> {
    val session = Common.Session.newBuilder().setSessionId(sessionId).setEndTimestamp(sessionEndTimestamp).build()
    val sessionMetadata = Common.SessionMetaData.newBuilder().setSessionId(sessionId).build()
    val info = Trace.TraceInfo.newBuilder().setToTimestamp((if (isOngoing) -1 else sessionEndTimestamp)).build()
    return CpuCaptureSessionArtifact(myProfilers, session, sessionMetadata, info)
  }
}
