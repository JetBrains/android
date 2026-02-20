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
package com.android.tools.idea.insights.ai

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.insights.CONNECTION1
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.testing.ui.FakeToolWindow
import com.android.tools.idea.testing.ui.createFakeToolWindow
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GeminiAiInsightsOnboardingProviderTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var scope: CoroutineScope
  private lateinit var geminiToolWindow: FakeToolWindow

  @Before
  fun setUp() {
    scope = AndroidCoroutineScope(projectRule.disposable)
    geminiToolWindow = createFakeToolWindow(projectRule.project, projectRule.disposable, GEMINI_TOOL_WINDOW_ID)
  }

  @Test
  fun `perform action hides button`() =
    runBlocking<Unit> {
      val provider = GeminiAiInsightsOnboardingProvider(projectRule.project)
      val buttonStateFlow = provider.buttonEnabledState().shareIn(scope, SharingStarted.Eagerly, 1)
      buttonStateFlow.first { it }

      // There exists a race condition between the registration of the tool window listener
      // and the performOnboardingAction call. If the tool window listener is registered
      // after the call, then we call again after a timeout. This is the workaround
      // implemented below.
      retryOnceWithTimeout {
        provider.performOnboardingAction(CONNECTION1)
        buttonStateFlow.first { !it }
      }

      geminiToolWindow.hide()
      buttonStateFlow.first { it }
    }

  private suspend fun retryOnceWithTimeout(timeoutMs: Long = 5000, retry: suspend () -> Unit) {
    try {
      withTimeout(timeoutMs) { retry() }
    } catch (_: TimeoutCancellationException) {
      retry()
    }
  }
}
