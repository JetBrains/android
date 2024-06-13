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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.EmptyActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BusyObject
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.AlertIcon
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import icons.StudioIcons
import java.awt.Component
import java.awt.Container
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

data class TabInfo(
  val deviceId: DeviceId,
  val content: Component,
  val container: Container,
  val displayView: AbstractDisplayView,
)

class FakeToolWindowManager(project: Project, tabs: List<TabInfo>) :
  ToolWindowHeadlessManagerImpl(project) {
  var toolWindow = FakeToolWindow(project, tabs, this)

  override fun getToolWindow(id: String?): ToolWindow? {
    return if (id == RUNNING_DEVICES_TOOL_WINDOW_ID) toolWindow else super.getToolWindow(id)
  }

  fun addContent(tabInfo: TabInfo) {
    toolWindow.addContent(tabInfo)
  }

  fun removeContent(tabInfo: TabInfo) {
    toolWindow.removeContent(tabInfo)
  }

  fun setSelectedContent(tabInfo: TabInfo) {
    toolWindow.setSelectedContent(tabInfo)
  }

  override fun invokeLater(runnable: Runnable) {
    runnable.run()
  }
}

class FakeToolWindow(
  project: Project,
  tabs: List<TabInfo>,
  private val manager: ToolWindowManager,
) : ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private val fakeContentManager = FakeContentManager()
  private var visible = false

  init {
    Disposer.register(disposable, fakeContentManager)

    val contents =
      tabs.map {
        val fakeComponent = FakeRunningDevicesComponent(it)
        FakeContent(disposable, fakeContentManager, fakeComponent)
      }

    contents.forEach {
      fakeContentManager.addContent(it)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  override fun addContentManagerListener(listener: ContentManagerListener) {
    fakeContentManager.addContentManagerListener(listener)
  }

  override fun getContentManager(): ContentManager {
    return fakeContentManager
  }

  override fun getContentManagerIfCreated(): ContentManager? {
    return fakeContentManager
  }

  fun addContent(tabInfo: TabInfo) {
    val fakeComponent = FakeRunningDevicesComponent(tabInfo)
    val fakeContent = FakeContent(disposable, fakeContentManager, fakeComponent)
    fakeContentManager.addContent(fakeContent)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  fun removeContent(tabInfo: TabInfo) {
    val content = findContent(tabInfo)
    fakeContentManager.removeContent(content!!, true)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  fun setSelectedContent(tabInfo: TabInfo) {
    val content = findContent(tabInfo)
    fakeContentManager.setSelectedContent(content!!)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  private fun findContent(tabInfo: TabInfo): Content? {
    return fakeContentManager.contents.find {
      (it.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) ==
        tabInfo.deviceId.serialNumber
    }
  }

  override fun show(runnable: Runnable?) {
    visible = true
    notifyStateChanged()
    runnable?.run()
  }

  override fun hide(runnable: Runnable?) {
    visible = false
    notifyStateChanged()
    runnable?.run()
  }

  override fun isVisible() = visible

  private fun notifyStateChanged() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(manager)
  }
}

/**
 * Fake implementation of ContentManager taken from ToolWindowHeadlessManagerImpl#MockContentManager
 */
class FakeContentManager : ContentManager {
  private val myDispatcher = EventDispatcher.create(ContentManagerListener::class.java)
  private val myContents: MutableList<Content> = ArrayList()
  private var mySelected: Content? = null

  override fun getReady(requestor: Any): ActionCallback {
    return ActionCallback.DONE
  }

  override fun addContent(content: Content) {
    addContent(content, -1)
  }

  override fun addContent(content: Content, order: Int) {
    myContents.add(if (order == -1) myContents.size else order, content)
    if (content is ContentImpl && content.getManager() == null) {
      content.manager = this
    }
    Disposer.register(this, content)
    val e =
      ContentManagerEvent(
        this,
        content,
        myContents.indexOf(content),
        ContentManagerEvent.ContentOperation.add,
      )
    myDispatcher.multicaster.contentAdded(e)
    if (mySelected == null) setSelectedContent(content)
  }

  override fun addSelectedContent(content: Content) {
    addContent(content)
    setSelectedContent(content)
  }

  override fun addContentManagerListener(l: ContentManagerListener) {
    if (Registry.`is`("ide.content.manager.listeners.order.fix")) {
      myDispatcher.listeners.add(l)
      return
    }
    myDispatcher.listeners.add(0, l)
  }

  override fun addDataProvider(provider: DataProvider) {}

  override fun canCloseAllContents(): Boolean {
    return false
  }

  override fun canCloseContents(): Boolean {
    return false
  }

  override fun findContent(displayName: String): Content? {
    for (each in myContents) {
      if (each.displayName == displayName) return each
    }
    return null
  }

  override fun getAdditionalPopupActions(content: Content): List<AnAction> {
    return emptyList()
  }

  override fun getCloseActionName(): String {
    return "close"
  }

  override fun getCloseAllButThisActionName(): String {
    return "closeallbutthis"
  }

  override fun getPreviousContentActionName(): String {
    return "previous"
  }

  override fun getNextContentActionName(): String {
    return "next"
  }

  override fun getComponent(): JComponent {
    return JLabel()
  }

  override fun getContent(component: JComponent): Content? {
    val contents = contents
    for (content in contents) {
      if (Comparing.equal(component, content.component)) {
        return content
      }
    }
    return null
  }

  override fun getContent(index: Int): Content? {
    return myContents[index]
  }

  override fun getContentCount(): Int {
    return myContents.size
  }

  override fun getContents(): Array<Content> {
    return myContents.toTypedArray()
  }

  override fun getIndexOfContent(content: Content): Int {
    return myContents.indexOf(content)
  }

  override fun getSelectedContent(): Content? {
    return mySelected
  }

  override fun getSelectedContents(): Array<Content> {
    return if (mySelected != null) arrayOf(mySelected!!) else arrayOf()
  }

  override fun isSelected(content: Content): Boolean {
    return content === mySelected
  }

  override fun removeAllContents(dispose: Boolean) {
    for (content in contents) {
      removeContent(content, dispose)
    }
  }

  override fun removeContent(content: Content, dispose: Boolean): Boolean {
    val wasSelected = mySelected === content
    val oldIndex = myContents.indexOf(content)
    if (!fireContentRemoveQuery(content, oldIndex) || !content.isValid) {
      return false
    }
    if (wasSelected) {
      removeFromSelection(content)
    }
    val result = myContents.remove(content)
    if (dispose) Disposer.dispose(content)
    val e =
      ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.remove)
    myDispatcher.multicaster.contentRemoved(e)
    val item = ContainerUtil.getFirstItem(myContents)
    if (item != null) {
      setSelectedContent(item)
    } else {
      mySelected = null
    }
    return result
  }

  override fun removeContent(
    content: Content,
    dispose: Boolean,
    requestFocus: Boolean,
    implicitFocus: Boolean,
  ): ActionCallback {
    removeContent(content, dispose)
    return ActionCallback.DONE
  }

  private fun fireContentRemoveQuery(content: Content, oldIndex: Int): Boolean {
    val event =
      ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.undefined)
    for (listener in myDispatcher.listeners) {
      listener.contentRemoveQuery(event)
      if (event.isConsumed) {
        return false
      }
    }
    return true
  }

  override fun removeContentManagerListener(l: ContentManagerListener) {
    myDispatcher.removeListener(l)
  }

  override fun removeFromSelection(content: Content) {
    val e =
      ContentManagerEvent(
        this,
        content,
        myContents.indexOf(mySelected),
        ContentManagerEvent.ContentOperation.remove,
      )
    myDispatcher.multicaster.selectionChanged(e)
  }

  override fun selectNextContent(): ActionCallback {
    return ActionCallback.DONE
  }

  override fun selectPreviousContent(): ActionCallback {
    return ActionCallback.DONE
  }

  override fun setSelectedContent(content: Content) {
    if (mySelected != null) {
      removeFromSelection(mySelected!!)
    }
    mySelected = content
    val e =
      ContentManagerEvent(
        this,
        content,
        myContents.indexOf(content),
        ContentManagerEvent.ContentOperation.add,
      )
    myDispatcher.multicaster.selectionChanged(e)
  }

  override fun setSelectedContentCB(content: Content): ActionCallback {
    setSelectedContent(content)
    return ActionCallback.DONE
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean) {
    setSelectedContent(content)
  }

  override fun setSelectedContentCB(content: Content, requestFocus: Boolean): ActionCallback {
    return setSelectedContentCB(content)
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean, forcedFocus: Boolean) {
    setSelectedContent(content)
  }

  override fun setSelectedContentCB(
    content: Content,
    requestFocus: Boolean,
    forcedFocus: Boolean,
  ): ActionCallback {
    return setSelectedContentCB(content)
  }

  override fun setSelectedContent(
    content: Content,
    requestFocus: Boolean,
    forcedFocus: Boolean,
    implicit: Boolean,
  ): ActionCallback {
    return setSelectedContentCB(content)
  }

  override fun requestFocus(content: Content?, forced: Boolean): ActionCallback {
    return ActionCallback.DONE
  }

  override fun dispose() {
    myContents.clear()
    mySelected = null
    myDispatcher.listeners.clear()
  }

  override fun isDisposed(): Boolean {
    return false
  }

  override fun isSingleSelection(): Boolean {
    return true
  }

  override fun getFactory(): ContentFactory {
    return ApplicationManager.getApplication().getService(ContentFactory::class.java)
  }
}

class FakeContent(
  private val disposable: Disposable,
  private val contentManager: ContentManager,
  private val fakeComponent: JComponent,
) : Content {
  init {
    Disposer.register(disposable, this)
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? = null

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {}

  override fun dispose() {}

  override fun getComponent() = fakeComponent

  override fun getPreferredFocusableComponent() = fakeComponent

  override fun setComponent(component: JComponent?) {}

  override fun setPreferredFocusableComponent(component: JComponent?) {}

  override fun setPreferredFocusedComponent(computable: Computable<out JComponent>?) {}

  override fun setIcon(icon: Icon?) {}

  override fun getIcon() = StudioIcons.LayoutInspector.Toolbar.SNAPSHOT

  override fun setDisplayName(displayName: String?) {}

  override fun getDisplayName() = "Fake Content"

  override fun setTabName(tabName: String?) {}

  override fun getTabName() = "Fake Tab"

  override fun getToolwindowTitle() = "Fake Tool Window"

  override fun setToolwindowTitle(toolwindowTitle: String?) {}

  override fun getDisposer() = disposable

  override fun setDisposer(disposer: Disposable) {}

  override fun setShouldDisposeContent(value: Boolean) {}

  override fun getDescription() = "Fake description"

  override fun setDescription(description: String?) {}

  override fun addPropertyChangeListener(l: PropertyChangeListener?) {}

  override fun removePropertyChangeListener(l: PropertyChangeListener?) {}

  override fun getManager() = contentManager

  override fun isSelected() = contentManager.selectedContent == this

  override fun release() {}

  override fun isValid() = true

  override fun setPinned(locked: Boolean) {}

  override fun isPinned() = false

  override fun setPinnable(pinnable: Boolean) {}

  override fun isPinnable() = true

  override fun isCloseable() = true

  override fun setCloseable(closeable: Boolean) {}

  override fun setActions(actions: ActionGroup?, place: String?, contextComponent: JComponent?) {}

  override fun getActions() = EmptyActionGroup()

  override fun setSearchComponent(comp: JComponent?) {}

  override fun getSearchComponent() = null

  override fun getPlace() = "fake place"

  override fun getActionsContextComponent() = JPanel()

  override fun setAlertIcon(icon: AlertIcon?) {}

  override fun getAlertIcon() = null

  override fun fireAlert() {}

  override fun getBusyObject() = null

  override fun setBusyObject(`object`: BusyObject?) {}

  override fun getSeparator() = "fake separator"

  override fun setSeparator(separator: String?) {}

  override fun setPopupIcon(icon: Icon?) {}

  override fun getPopupIcon() = StudioIcons.LayoutInspector.Toolbar.CLEAR_OVERLAY

  override fun setExecutionId(executionId: Long) {}

  override fun getExecutionId() = 1L
}

class FakeRunningDevicesComponent(private val tabInfo: TabInfo) : JPanel(), DataProvider {
  init {
    tabInfo.container.add(tabInfo.content)
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      SERIAL_NUMBER_KEY.name -> tabInfo.deviceId.serialNumber
      STREAMING_CONTENT_PANEL_KEY.name -> tabInfo.content
      DISPLAY_VIEW_KEY.name -> tabInfo.displayView
      DEVICE_ID_KEY.name -> tabInfo.deviceId
      else -> null
    }
  }
}
