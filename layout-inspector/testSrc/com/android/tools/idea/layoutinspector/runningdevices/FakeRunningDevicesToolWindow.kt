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

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.DEVICE_TYPE_KEY
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.core.DeviceDisplayListener
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayOwner
import com.android.tools.idea.streaming.core.DisplayView
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

data class TabInfo(
  val deviceId: DeviceId,
  val content: BorderLayoutPanel,
  val container: Container,
  val displays: List<DisplayView>,
  val deviceType: DeviceType = DeviceType.HANDHELD,
) {
  init {
    displays.forEach { content.add(it.component) }
  }
}

fun addContent(toolWindow: ToolWindow, tabInfo: TabInfo) {
  val fakeComponent = FakeRunningDevicesComponent(tabInfo)
  val fakeContent = FakeContent(toolWindow.disposable, toolWindow.contentManager, fakeComponent)
  toolWindow.contentManager.addContent(fakeContent)
  if (toolWindow.contentManager.selectedContent == null) {
    toolWindow.contentManager.setSelectedContent(fakeContent)
  }
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
}

fun removeContent(toolWindow: ToolWindow, tabInfo: TabInfo) {
  val content = findContent(toolWindow, tabInfo) ?: return
  val wasSelected = toolWindow.contentManager.isSelected(content)
  val index = toolWindow.contentManager.getIndexOfContent(content)
  toolWindow.contentManager.removeContent(content, true)

  if (wasSelected) {
    val first = toolWindow.contentManager.contents.firstOrNull()
    if (first != null) {
      toolWindow.contentManager.setSelectedContent(first)
    }
  }
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
}

fun setSelectedContent(toolWindow: ToolWindow, tabInfo: TabInfo) {
  val content = findContent(toolWindow, tabInfo)
  toolWindow.contentManager.setSelectedContent(content!!)
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
}

private fun findContent(toolWindow: ToolWindow, tabInfo: TabInfo): Content? {
  return toolWindow.contentManager.contents.find {
    val component = it.component
    component is FakeRunningDevicesComponent && component.tabInfo.deviceId == tabInfo.deviceId
  }
}

fun ToolWindow.getContent(deviceId: DeviceId): Content {
  return contentManager.contents.first {
    it.component is FakeRunningDevicesComponent && (it.component as FakeRunningDevicesComponent).tabInfo.deviceId == deviceId
  }
}

class FakeContent(disposable: Disposable, contentManager: ContentManager, fakeComponent: JComponent) :
  ContentImpl(fakeComponent, "Fake Content", true) {
  init {
    Disposer.register(disposable, this)
    setManager(contentManager)
  }
}

class FakeRunningDevicesComponent(val tabInfo: TabInfo) : JPanel(), UiDataProvider, DisplayOwner {
  init {
    tabInfo.container.add(tabInfo.content)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[SERIAL_NUMBER_KEY] = tabInfo.deviceId.serialNumber
    sink[STREAMING_CONTENT_PANEL_KEY] = tabInfo.content
    sink[DISPLAY_VIEW_KEY] = tabInfo.displays.first()
    sink[DEVICE_ID_KEY] = tabInfo.deviceId
    sink[DEVICE_TYPE_KEY] = tabInfo.deviceType
  }

  override fun addDeviceDisplayListener(listener: DeviceDisplayListener) {}

  override fun removeDeviceDisplayListener(listener: DeviceDisplayListener) {}
}

/** Fake implementation of ContentManager taken from ToolWindowHeadlessManagerImpl#MockContentManager */
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
    val e = ContentManagerEvent(this, content, myContents.indexOf(content), ContentManagerEvent.ContentOperation.add)
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
    val e = ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.remove)
    myDispatcher.multicaster.contentRemoved(e)
    val item = ContainerUtil.getFirstItem(myContents)
    if (item != null) {
      setSelectedContent(item)
    } else {
      mySelected = null
    }
    return result
  }

  override fun removeContent(content: Content, dispose: Boolean, requestFocus: Boolean, implicitFocus: Boolean): ActionCallback {
    removeContent(content, dispose)
    return ActionCallback.DONE
  }

  private fun fireContentRemoveQuery(content: Content, oldIndex: Int): Boolean {
    val event = ContentManagerEvent(this, content, oldIndex, ContentManagerEvent.ContentOperation.undefined)
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
    val e = ContentManagerEvent(this, content, myContents.indexOf(mySelected), ContentManagerEvent.ContentOperation.remove)
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
    val e = ContentManagerEvent(this, content, myContents.indexOf(content), ContentManagerEvent.ContentOperation.add)
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

  override fun setSelectedContentCB(content: Content, requestFocus: Boolean, forcedFocus: Boolean): ActionCallback {
    return setSelectedContentCB(content)
  }

  override fun setSelectedContent(content: Content, requestFocus: Boolean, forcedFocus: Boolean, implicit: Boolean): ActionCallback {
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
