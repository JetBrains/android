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

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.UseConstructor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProfilerTaskHandlerTest {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false,  ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("ProfilerTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
  }

  /**
   * Constructs and returns a mock {@link ProfilerTaskHandler} using a mocked {@link SessionsManager} and {@link TaskArgs} instance.
   *
   * Configured to return true on ProfilerTaskHandler#loadTask method invocation and to call the real ProfilerTaskHandler#enter method if
   * invoked via the mock.
   */
  private fun createMockProfilerTaskHandler(mockSessionsManager: SessionsManager, mockArgs: TaskArgs): ProfilerTaskHandler {
    val mockProfilerTaskHandler = mock<ProfilerTaskHandler>(useConstructor = UseConstructor.withArguments(mockSessionsManager)).apply{
      whenever(loadTask(mockArgs)).thenReturn(true)
      whenever(enter(mockArgs)).thenCallRealMethod()
    }
    return mockProfilerTaskHandler
  }

  @Test
  fun testEnterWithAliveSession() {
    val mockArgs = mock<TaskArgs>()

    val mockSessionsManager = mock<SessionsManager>().apply {
      whenever(this.studioProfilers).thenReturn(myProfilers)
      whenever(this.isSessionAlive).thenReturn(true)
      whenever(this.selectedSession).thenReturn(Common.Session.getDefaultInstance())
    }

    // Creating a mock instance of SessionsManager that returns that the session is currently alive.
    val mockProfilerTaskHandler = createMockProfilerTaskHandler(mockSessionsManager, mockArgs)
    val args = mockProfilerTaskHandler.enter(mockArgs)
    // Verify that the startTask method was invoked once.
    verify(mockProfilerTaskHandler, times(1)).startTask(mockArgs)
    // Verify that the loadTask method was not invoked.
    verify(mockProfilerTaskHandler, never()).loadTask(any())
    assertThat(args).isTrue()
  }

  @Test
  fun testEnterWithDeadSession() {
    val mockArgs = mock<TaskArgs>()
    // Creating a mock instance of SessionsManager that returns that the session is currently not alive.
    val mockSessionsManager = mock<SessionsManager>().apply {
      whenever(this.studioProfilers).thenReturn(myProfilers)
      whenever(this.isSessionAlive).thenReturn(false)
    }
    val mockProfilerTaskHandler = createMockProfilerTaskHandler(mockSessionsManager, mockArgs)
    val args = mockProfilerTaskHandler.enter(mockArgs)
    // Verify that the startTask method was never invoked.
    verify(mockProfilerTaskHandler, never()).startTask(mockArgs)
    // Verify that the loadTask method was invoked once.
    verify(mockProfilerTaskHandler, times(1)).loadTask(any())
    assertThat(args).isTrue()
  }
}