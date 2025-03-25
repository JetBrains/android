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

import com.android.tools.idea.insights.Connection
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GeminiAiInsightsOnboardingProvider(private val project: Project) :
  InsightsOnboardingProvider {
  private val studioBotToolWindow: ToolWindow?
    get() = ToolWindowManager.getInstance(project).getToolWindow(GEMINI_TOOL_WINDOW_ID)

  private val studioBotToolWindowVisibilityListener =
    project.messageBus
      .subscribeAsFlow(ToolWindowManagerListener.TOPIC) {
        if (studioBotToolWindow?.isDisposed != false) throw CancellationException()
        trySend(studioBotToolWindow?.isVisible == true)
        object : ToolWindowManagerListener {
          @Suppress("UnstableApiUsage")
          override fun stateChanged(
            toolWindowManager: ToolWindowManager,
            toolWindow: ToolWindow,
            changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
          ) {
            if (toolWindow.id != GEMINI_TOOL_WINDOW_ID) return
            trySend(toolWindow.isVisible)
          }
        }
      }
      .distinctUntilChanged()

  override fun performOnboardingAction(connection: Connection) {
    studioBotToolWindow?.show()
  }

  override fun buttonEnabledState() = studioBotToolWindowVisibilityListener.map { !it }
}
