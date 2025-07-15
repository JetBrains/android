/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.testFramework.replaceService
import icons.StudioIcons
import org.mockito.kotlin.mock
import javax.swing.Icon

/** Creates a [FakeToolWindow] for testing. */
internal fun createFakeToolWindow(
  windowFactory: ToolWindowFactory,
  toolWindowId: String,
  project: Project,
  parentDisposable: Disposable,
): FakeToolWindow {
  val windowManager = FakeToolWindowManager(windowFactory, toolWindowId, project)
  project.replaceService(ToolWindowManager::class.java, windowManager, parentDisposable)
  val toolWindow = windowManager.toolWindow
  assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
  windowFactory.init(toolWindow)
  return toolWindow
}

internal class FakeToolWindow(
  private val windowFactory: ToolWindowFactory,
  private val manager: ToolWindowManager,
  project: Project,
) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

  var tabActions: List<AnAction> = emptyList()
    private set
  var titleActions: List<AnAction> = emptyList()
    private set
  private var available = true
  private var visible = false
  private var active = false
  private var type = ToolWindowType.DOCKED
  private var icon = StudioIcons.Shell.ToolWindows.EMULATOR
  private val decorator = mock<InternalDecorator>()

  override fun setAvailable(available: Boolean) {
    this.available = available
  }

  override fun isAvailable(): Boolean {
    return available
  }

  override fun getDecorator(): InternalDecorator {
    return decorator
  }

  override fun show(runnable: Runnable?) {
    if (!visible) {
      windowFactory.createToolWindowContent(project, this)
      visible = true
      notifyStateChanged(ToolWindowManagerListener.ToolWindowManagerEventType.ShowToolWindow)
      runnable?.run()
    }
  }

  override fun hide(runnable: Runnable?) {
    if (visible) {
      visible = false
      notifyStateChanged(ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow)
      runnable?.run()
    }
  }

  override fun activate(runnable: Runnable?) {
    active = true
    super.activate(runnable)
  }

  override fun isVisible() = visible

  override fun isActive() = active

  override fun setTabActions(vararg actions: AnAction) {
    tabActions = listOf(*actions)
  }

  override fun setTitleActions(actions: List<AnAction>) {
    this.titleActions = actions
  }

  override fun getType(): ToolWindowType {
    return type
  }

  override fun setType(type: ToolWindowType, runnable: Runnable?) {
    this.type = type
    runnable?.run()
  }

  override fun getIcon(): Icon {
    return icon
  }

  override fun setIcon(icon: Icon) {
    this.icon = icon
  }

  private fun notifyStateChanged(changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager, changeType)
  }
}

private class FakeToolWindowManager(
  windowFactory: ToolWindowFactory,
  private val toolWindowId: String,
  project: Project,
) : ToolWindowHeadlessManagerImpl(project) {
  var toolWindow = FakeToolWindow(windowFactory, this, project)

  override fun getToolWindow(id: String?): ToolWindow? {
    return if (id == toolWindowId) toolWindow else super.getToolWindow(id)
  }

  override fun invokeLater(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable)
  }
}
