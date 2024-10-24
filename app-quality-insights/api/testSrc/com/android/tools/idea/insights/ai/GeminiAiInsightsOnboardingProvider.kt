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
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FakeToolWindowManager(project: Project, private val geminiToolWindow: FakeGeminiToolWindow) :
  ToolWindowHeadlessManagerImpl(project) {
  override fun getToolWindow(id: String?): ToolWindow? {
    return geminiToolWindow
  }
}

class FakeGeminiToolWindow(project: Project) :
  ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private val publisher = project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC)
  private val toolWindowManager: ToolWindowManager
    get() = ToolWindowManager.getInstance(project)

  private var visible = false

  override fun getId() = GEMINI_TOOL_WINDOW_ID

  override fun isVisible(): Boolean {
    return visible
  }

  override fun show(runnable: Runnable?) {
    visible = true
    publisher.stateChanged(
      toolWindowManager,
      this,
      ToolWindowManagerListener.ToolWindowManagerEventType.ShowToolWindow,
    )
  }

  override fun hide() {
    visible = false
    publisher.stateChanged(
      toolWindowManager,
      this,
      ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow,
    )
  }
}

class GeminiAiInsightsOnboardingProviderTest {

  @get:Rule val projectRule = ProjectRule()

  private lateinit var scope: CoroutineScope
  private lateinit var geminiToolWindow: FakeGeminiToolWindow

  @Before
  fun setUp() {
    scope = AndroidCoroutineScope(projectRule.disposable)
    geminiToolWindow = FakeGeminiToolWindow(projectRule.project)
    val manager = FakeToolWindowManager(projectRule.project, geminiToolWindow)
    projectRule.project.replaceService(
      ToolWindowManager::class.java,
      manager,
      projectRule.disposable,
    )
  }

  @Test
  fun `perform action hides button`() =
    runBlocking<Unit> {
      val provider = GeminiAiInsightsOnboardingProvider(projectRule.project)
      val buttonStateFlow =
        provider.buttonEnabledState().stateIn(scope, SharingStarted.Eagerly, true)

      provider.performOnboardingAction(CONNECTION1)
      buttonStateFlow.first { !it }

      geminiToolWindow.hide()
      buttonStateFlow.first { it }
    }
}
