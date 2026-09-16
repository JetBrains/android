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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.layoutinspector.runningdevices.RunningDevicesStateObserver
import com.android.tools.idea.streaming.core.DeviceDisplayListener
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayOwner
import com.android.tools.idea.streaming.core.DisplayView
import com.android.tools.idea.streaming.core.STREAMING_CONTENT_PANEL_KEY
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.ThreadingAssertions
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Class grouping components from a Running Devices tab. Used to inject Layout Inspector in the tab. These components are disposed as soon
 * as the tab is not visible or is not the main selected tab. For this reason they should not be kept around if they don't belong to the
 * selected tab.
 *
 * @param tabContentPanel The component containing the main content of the tab (the display).
 */
class TabComponents(val disposable: Disposable, val tabContentPanel: JComponent, private val displayOwner: DisplayOwner) : Disposable {

  private val lock = Any()

  @GuardedBy("lock") private val _displayList = MutableStateFlow<List<DisplayView>>(emptyList())
  /**
   * The list of [AbstractDisplayView] from running devices. Each entry corresponds to a display on the device. Layout Inspector UI is
   * rendered on top of each display.
   */
  val displayList = synchronized(lock) { _displayList.asStateFlow() }

  private val displayListener =
    object : DeviceDisplayListener {
      @UiThread
      override fun displayAdded(displayView: DisplayView) {
        synchronized(lock) { _displayList.value += displayView }
      }

      @UiThread
      override fun displayRemoved(displayView: DisplayView) {
        synchronized(lock) { _displayList.value -= displayView }
      }
    }

  init {
    Disposer.register(disposable, this)

    synchronized(lock) { _displayList.value = tabContentPanel.allChildren().filterIsInstance<DisplayView>() }

    displayOwner.addDeviceDisplayListener(displayListener)
  }

  override fun dispose() {
    displayOwner.removeDeviceDisplayListener(displayListener)
  }
}

/** Recursively get all the children of [Container] */
private fun Container.allChildren(): List<Component> {
  return components.flatMap { child ->
    if (child is Container) {
      listOf(child) + child.allChildren()
    } else {
      listOf(child)
    }
  }
}

/** Creates a new [TabComponents] object for the provided [deviceId] */
fun createTabComponents(project: Project, deviceId: DeviceId): TabComponents {
  ThreadingAssertions.assertEventDispatchThread()
  val selectedTabContent = RunningDevicesStateObserver.getInstance(project).getTabContent(deviceId)

  val streamingDevicePanel = checkNotNull(selectedTabContent?.component)

  val selectedTabDataProvider = DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, streamingDevicePanel)

  val streamingContentPanel = STREAMING_CONTENT_PANEL_KEY.getData(selectedTabDataProvider)

  checkNotNull(streamingContentPanel)

  return TabComponents(
    disposable = selectedTabContent,
    tabContentPanel = streamingContentPanel,
    displayOwner = streamingDevicePanel as DisplayOwner,
  )
}
