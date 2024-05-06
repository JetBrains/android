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
import com.android.tools.profilers.InterimStage
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.args.TaskArgs
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings

class SingleArtifactTaskHandlerTest {

  /**
   * Constructs and returns a mock {@link SingleArtifactTaskHandler} using a mocked {@link SessionsManager}.
   *
   * Configured to call the real SingleArtifactTaskHandler#enter method if invoked via the mock.
   */
  private fun createMockSingleArtifactTaskHandler(): SingleArtifactTaskHandler<InterimStage> {
    val mockSessionsManager = MockitoKt.mock<SessionsManager>()
    val mockSingleArtifactTaskHandler = MockitoKt.mock<SingleArtifactTaskHandler<InterimStage>>(
      withSettings().useConstructor(mockSessionsManager)).apply {
      MockitoKt.whenever(enter(any())).thenCallRealMethod()
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
}
