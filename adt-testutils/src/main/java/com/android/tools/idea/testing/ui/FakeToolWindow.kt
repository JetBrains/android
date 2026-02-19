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
package com.android.tools.idea.testing.ui

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.testFramework.replaceService
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon
import org.mockito.kotlin.mock

/** Creates a [FakeToolWindow] for testing. */
fun createFakeToolWindow(
  project: Project,
  parentDisposable: Disposable,
  toolWindowId: String,
  icon: Icon = EmptyIcon.ICON_16,
  windowFactory: ToolWindowFactory = SimpleToolWindowFactory(),
): FakeToolWindow {
  val windowManager = FakeToolWindowManager(windowFactory, toolWindowId, icon, project)
  project.replaceService(ToolWindowManager::class.java, windowManager, parentDisposable)
  Disposer.register(parentDisposable) { toolWindowBalloons.clear() }
  val toolWindow = windowManager.toolWindow
  assertThat(windowFactory.shouldBeAvailable(project)).isTrue()
  windowFactory.init(toolWindow)
  return toolWindow
}

class FakeToolWindow
internal constructor(
  private val windowFactory: ToolWindowFactory,
  private var icon: Icon,
  private val manager: ToolWindowManager,
  project: Project,
  private val toolWindowId: String,
) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {

  var tabActions: List<AnAction> = emptyList()
    private set

  var titleActions: List<AnAction> = emptyList()
    private set

  var hideOnEmptyContext: Boolean = false
    private set

  private var available = true
  private var visible = false
  private var active = false
  private var type = ToolWindowType.DOCKED
  private val decorator = mock<InternalDecorator>()

  override fun setToHideOnEmptyContent(value: Boolean) {
    hideOnEmptyContext = value
  }

  override fun getId(): String = toolWindowId

  override fun setAvailable(value: Boolean) {
    available = value
  }

  override fun isAvailable(): Boolean = available

  override fun getDecorator(): InternalDecorator = decorator

  override fun show(runnable: Runnable?) {
    if (!visible) {
      windowFactory.createToolWindowContent(project, this)
      visible = true
      notifyStateChanged(ToolWindowManagerEventType.ShowToolWindow)
      runnable?.run()
    }
  }

  override fun hide(runnable: Runnable?) {
    if (visible) {
      visible = false
      notifyStateChanged(ToolWindowManagerEventType.HideToolWindow)
      runnable?.run()
    }
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {
    active = true
    notifyStateChanged(ToolWindowManagerEventType.ActivateToolWindow)
    runnable?.run()
  }

  override fun isVisible() = visible

  override fun isActive() = active

  override fun setTabActions(vararg actions: AnAction) {
    tabActions = listOf(*actions)
  }

  override fun setTitleActions(actions: List<AnAction>) {
    titleActions = actions
  }

  override fun getType(): ToolWindowType = type

  override fun setType(type: ToolWindowType, runnable: Runnable?) {
    this.type = type
    runnable?.run()
  }

  override fun getIcon(): Icon = icon

  override fun setIcon(icon: Icon) {
    this.icon = icon
  }

  override fun addContentManagerListener(listener: ContentManagerListener) {
    contentManager.addContentManagerListener(listener)
  }

  private fun notifyStateChanged(changeType: ToolWindowManagerEventType) {
    val publisher = project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC)
    @Suppress("UnstableApiUsage") publisher.stateChanged(manager, this, changeType)
    if (changeType == ToolWindowManagerEventType.ShowToolWindow) {
      publisher.toolWindowShown(this)
    }
  }
}

private class FakeToolWindowManager(windowFactory: ToolWindowFactory, toolWindowId: String, icon: Icon, project: Project) :
  ToolWindowHeadlessManagerImpl(project) {

  val toolWindow = FakeToolWindow(windowFactory, icon, this, project, toolWindowId)

  override fun getToolWindow(id: String?): ToolWindow? = if (id == toolWindow.id) toolWindow else super.getToolWindow(id)

  override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    toolWindowBalloons.add(options)
  }

  override fun invokeLater(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable)
  }
}

class SimpleToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {}
}

val toolWindowBalloons = mutableListOf<ToolWindowBalloonShowOptions>()
