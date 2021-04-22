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
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl
import org.junit.Rule
import org.junit.Test

private val MODERN_PROCESS = MODERN_DEVICE.createProcess()
private val LEGACY_PROCESS = LEGACY_DEVICE.createProcess()
private val OLDER_LEGACY_PROCESS = OLDER_LEGACY_DEVICE.createProcess()

class LayoutInspectorToolWindowFactoryTest {
  private class FakeToolWindowManager(project: Project, private val toolWindow: ToolWindow) : ToolWindowHeadlessManagerImpl(project) {
    var notificationText = ""

    override fun getToolWindow(id: String?): ToolWindow {
      return toolWindow
    }

    override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
      notificationText = options.htmlBody
    }
  }

  private class FakeToolWindow(
    project: Project,
    private val listener: LayoutInspectorToolWindowManagerListener
  ) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
    var shouldBeAvailable = true
    var visible = false
    val manager = FakeToolWindowManager(project, this)

    override fun setAvailable(available: Boolean, runnable: Runnable?) {
      shouldBeAvailable = available
    }

    override fun isAvailable() = shouldBeAvailable

    override fun show(runnable: Runnable?) {
      visible = true
      listener.stateChanged(manager)
    }

    override fun hide(runnable: Runnable?) {
      visible = false
      listener.stateChanged(manager)
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
  fun clientOnlyLaunchedIfWindowIsNotMinimized() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)
    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.visible).isFalse()
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()

    toolWindow.show()
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()
  }

  @Test
  fun testShowInspectionNotificationWhenInspectorIsRunning() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)

    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    // bubble isn't shown when inspection not running
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isEmpty()

    // Attach to a fake process.
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)

    // Check bubble is shown.
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isNotEmpty()

    // Message is shown each time.
    toolWindow.manager.notificationText = ""
    toolWindow.show()
    toolWindow.hide()
    assertThat(toolWindow.manager.notificationText).isNotEmpty()
  }

  @Test
  fun clientCanBeDisconnectedWhileMinimized() {
    val listener = LayoutInspectorToolWindowManagerListener(inspectorRule.project, inspectorRule.launcher)
    val toolWindow = FakeToolWindow(inspectorRule.project, listener)

    toolWindow.show()
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    toolWindow.hide()
    assertThat(inspectorRule.inspectorClient.isConnected).isTrue()

    inspectorRule.processNotifier.fireDisconnected(LEGACY_PROCESS)
    assertThat(inspectorRule.inspectorClient.isConnected).isFalse()
  }

  @Test
  fun testCreateProcessesModel() {
    val factory = LayoutInspectorToolWindowFactory()
    val model = factory.createProcessesModel(inspectorRule.project, inspectorRule.processNotifier, MoreExecutors.directExecutor())
    // Verify that devices older than M will not be included in the processes model:
    inspectorRule.processNotifier.fireConnected(OLDER_LEGACY_PROCESS)
    assertThat(model.processes).isEmpty()
    // But an M device will:
    inspectorRule.processNotifier.fireConnected(LEGACY_PROCESS)
    assertThat(model.processes.size).isEqualTo(1)
    // And newer devices as well:
    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    assertThat(model.processes.size).isEqualTo(2)
  }
}