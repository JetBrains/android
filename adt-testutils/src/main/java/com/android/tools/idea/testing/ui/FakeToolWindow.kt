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
import com.intellij.openapi.ui.Splitter
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
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.SmartList
import com.intellij.util.ui.EmptyIcon
import java.awt.Container
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
) : ToolWindowHeadlessManagerImpl.MockToolWindow(project, FakeContentManager()) {

  var tabActions: List<AnAction> = emptyList()
    private set

  var titleActions: List<AnAction> = emptyList()
    private set

  var hideOnEmptyContext: Boolean = false
    private set

  private var available = true
  private var visible = false
  private var active = false
  private var focused = false
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
    focused = autoFocusContents
    notifyStateChanged(ToolWindowManagerEventType.ActivateToolWindow)
    runnable?.run()
  }

  fun isFocused(): Boolean = visible && focused

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

  companion object {
    fun split(content: Content, dropSide: Int, dropIndex: Int = -1) {
      val contentManager = content.manager
      if (contentManager != null) {
        (contentManager as FakeContentManager).splitWithContent(content, dropSide, dropIndex)
      }
    }

    fun unsplit(contentManager: ContentManager, toSelect: Content?) {
      (contentManager as FakeContentManager).unsplit(toSelect)
    }
  }
}

private class FakeToolWindowManager(windowFactory: ToolWindowFactory, toolWindowId: String, icon: Icon, project: Project) :
  ToolWindowHeadlessManagerImpl(project) {

  val toolWindow = FakeToolWindow(windowFactory, icon, this, project, toolWindowId)

  override fun doRegisterToolWindow(id: String): ToolWindow = doRegisterToolWindow(id, toolWindow)

  override fun getToolWindow(id: String?): ToolWindow? = if (id == toolWindow.id) toolWindow else super.getToolWindow(id)

  override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    toolWindowBalloons.add(options)
  }

  override fun invokeLater(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable)
  }
}

@Suppress("UnstableApiUsage")
class FakeContentManager : ToolWindowHeadlessManagerImpl.MockContentManager() {
  private val nestedManagers = SmartList<FakeContentManager>()
  private var parent: FakeContentManager? = null
  private var splitUnsplitInProgress = false
  private val internalDecorator: InternalDecoratorImpl
  private var splitter: Splitter? = null
  private val panel: JComponent = JBPanelWithEmptyText()
  private val treeLock: Any = JPanel().treeLock

  init {
    internalDecorator = createInternalDecorator(this)
    internalDecorator.add(panel)
  }

  override fun getComponent(): JComponent {
    return panel
  }

  override fun getContentsRecursively(): List<Content> {
    val result = mutableListOf<Content>()
    for (content in contents) {
      result.add(content)
    }

    for (child in nestedManagers) {
      result.addAll(child.getContentsRecursively())
    }
    return result
  }

  override fun getSelectedContents(): Array<Content> {
    val result = mutableListOf<Content>()
    selectedContent?.let { result.add(it) }
    for (child in nestedManagers) {
      for (content in child.getSelectedContents()) {
        result.add(content)
      }
    }
    return result.toTypedArray<Content>()
  }

  override fun getDecorator(): InternalDecorator = internalDecorator

  fun splitWithContent(content: Content, dropSide: Int, dropIndex: Int) {
    if (dropSide == -1 || dropSide == SwingConstants.CENTER || dropIndex >= 0) {
      addContent(content, dropIndex)
      return
    }
    val firstChild = FakeContentManager()
    Disposer.register(this, firstChild)
    val secondChild = FakeContentManager()
    Disposer.register(this, secondChild)
    addNestedManager(firstChild)
    addNestedManager(secondChild)
    val contents = contents.toMutableList()
    if (!contents.contains(content)) {
      contents.add(content)
    }
    for (c in contents) {
      val first = dropSide == SwingConstants.LEFT || dropSide == SwingConstants.TOP
      moveContent(c, if ((c !== content) xor first) firstChild else secondChild)
    }

    val isVertical = dropSide == SwingConstants.TOP || dropSide == SwingConstants.BOTTOM
    splitter = Splitter(isVertical, 0.5f)
    internalDecorator.remove(panel)
    internalDecorator.add(splitter)
    splitter!!.setFirstComponent(firstChild.internalDecorator)
    splitter!!.setSecondComponent(secondChild.internalDecorator)
  }

  fun unsplit(toSelect: Content?) {
    if (nestedManagers.isEmpty()) {
      parent?.unsplit(toSelect)
      return
    }
    if (splitUnsplitInProgress) {
      return
    }

    splitUnsplitInProgress = true
    try {
      for (child in nestedManagers) {
        if (child.isSplit()) {
          raise(child)
          return
        }
      }
      for (child in nestedManagers) {
        for (c in child.contents) {
          child.moveContent(c, this)
        }
      }
      toSelect?.manager?.setSelectedContent(toSelect)
      for (child in nestedManagers) {
        Disposer.dispose(child)
      }
      nestedManagers.clear()
      splitter = null
    } finally {
      splitUnsplitInProgress = false
    }
  }

  private fun isSplit(): Boolean {
    return !nestedManagers.isEmpty()
  }

  private fun moveContent(content: Content, target: ToolWindowHeadlessManagerImpl.MockContentManager) {
    val initialState = content.getUserData(Content.TEMPORARY_REMOVED_KEY)
    try {
      splitUnsplitInProgress = true
      content.putUserData(Content.TEMPORARY_REMOVED_KEY, java.lang.Boolean.TRUE)
      val owner = content.manager
      owner?.removeContent(content, false)
      (content as ContentImpl).setManager(target)
      target.addContent(content)
    } finally {
      content.putUserData(Content.TEMPORARY_REMOVED_KEY, initialState)
      splitUnsplitInProgress = false
    }
  }

  private fun addNestedManager(manager: FakeContentManager) {
    manager.parent = this
    nestedManagers.add(manager)
    Disposer.register(manager) { removeNestedManager(manager) }
  }

  private fun removeNestedManager(manager: FakeContentManager) {
    nestedManagers.remove(manager)
  }

  @Suppress("UnstableApiUsage")
  private fun createInternalDecorator(contentManager: ContentManager): InternalDecoratorImpl {
    val mockDecorator = mock<InternalDecoratorImpl>(defaultAnswer = CALLS_REAL_METHODS)
    try {
      val field = Container::class.java.getDeclaredField("component")
      field.isAccessible = true
      field.set(mockDecorator, ArrayList<Any>())
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
    doAnswer { "" }.whenever(mockDecorator).toString() // To avoid NPE while debugging.
    doAnswer { treeLock }.whenever(mockDecorator).treeLock
    doAnswer { contentManager }.whenever(mockDecorator).contentManager
    doAnswer { true }.whenever(mockDecorator).isVisible

    doAnswer { FakeToolWindow.unsplit(contentManager, it.getArgument(0)) }.whenever(mockDecorator).unsplit(any())

    doAnswer { FakeToolWindow.split(it.getArgument(0), it.getArgument(1), it.getArgument(2)) }
      .whenever(mockDecorator)
      .splitWithContent(any(), anyInt(), anyInt())

    doAnswer { false }.whenever(mockDecorator).isSplitUnsplitInProgress
    return mockDecorator
  }

  private fun raise(child: FakeContentManager) {
    throw NotImplementedError()
  }
}

class SimpleToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {}
}

val toolWindowBalloons = mutableListOf<ToolWindowBalloonShowOptions>()
