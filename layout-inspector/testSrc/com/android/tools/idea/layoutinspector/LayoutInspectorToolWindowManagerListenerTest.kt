/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import org.junit.Rule
import org.junit.Test

private val LEGACY_PROCESS = LEGACY_DEVICE.createProcess()

class LayoutInspectorToolWindowManagerListenerTest {
  private class FakeToolWindow(
    project: Project,
    private val toolWindowManager: ToolWindowManager,
    private val listener: LayoutInspectorToolWindowManagerListener
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var shouldBeAvailable = true
    var visible = true

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      shouldBeAvailable = available
    }

    override fun isAvailable() = shouldBeAvailable

    override fun show(runnable: Runnable?) {
      visible = true
      listener.stateChanged(toolWindowManager)
    }

    override fun hide(runnable: Runnable?) {
      visible = false
      listener.stateChanged(toolWindowManager)
    }

    override fun isVisible(): Boolean {
      return visible
    }
  }

  @get:Rule
  val inspectorRule = LayoutInspectorRule(LegacyClientProvider(), projectRule = AndroidProjectRule.inMemory().initAndroid(false)) {
    listOf(LEGACY_PROCESS.name)
  }

  @Test
  fun testShowInspectionNotificationWhenInspectorIsRunning() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.inspector)

    var notificationText = ""

    lateinit var toolWindow: ToolWindow
    val toolWindowManager = object : ToolWindowHeadlessManagerImpl(inspectorRule.project) {

      override fun getToolWindow(id: String?): ToolWindow {
        return toolWindow
      }

      override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String) {
        notificationText = htmlBody
      }
    }
    toolWindow = FakeToolWindow(inspectorRule.project, toolWindowManager, listener)

    // bubble isn't shown when inspection not running
    toolWindow.show()
    toolWindow.hide()
    assertThat(notificationText).isEmpty()

    // Attach to a fake process.
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)

    // Check bubble is shown.
    toolWindow.show()
    toolWindow.hide()
    assertThat(notificationText).isNotEmpty()

    // Message is only shown once
    notificationText = ""
    toolWindow.show()
    toolWindow.hide()
    assertThat(notificationText).isEmpty()
  }
}