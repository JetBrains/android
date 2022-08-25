/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.mocks

import com.android.tools.idea.FutureValuesTracker
import com.android.tools.idea.device.monitor.DeviceMonitorModel
import com.android.tools.idea.device.monitor.DeviceMonitorProgressListener
import com.android.tools.idea.device.monitor.DeviceMonitorView
import com.android.tools.idea.device.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.monitor.DeviceNameRendererFactory
import com.android.tools.idea.device.monitor.ProcessTreeNode
import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceListService
import com.android.tools.idea.device.monitor.ui.DeviceMonitorViewImpl
import com.android.tools.idea.device.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.monitor.ui.menu.item.MenuContext
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import javax.swing.JComboBox

class MockDeviceMonitorView(
  project: Project,
  rendererFactory: DeviceNameRendererFactory,
  model: DeviceMonitorModel
) : DeviceMonitorView {
  private val viewImpl = DeviceMonitorViewImpl(project, rendererFactory, model)
  val modelListener = MockModelListener()
  val viewListener = MockDeviceMonitorViewListener()

  val startRefreshTracker = FutureValuesTracker<String>()
  private val stopRefreshTracker = FutureValuesTracker<String>()
  private val reportErrorRelatedToServiceTracker = FutureValuesTracker<String>()
  val reportErrorRelatedToDeviceTracker = FutureValuesTracker<String>()

  init {
    viewImpl.addListener(viewListener)
    model.addListener(modelListener)
  }

  override fun addListener(listener: DeviceMonitorViewListener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: DeviceMonitorViewListener) {
    viewImpl.removeListener(listener)
  }

  override fun setup() {
    viewImpl.setup()
  }

  override fun startRefresh(text: String) {
    viewImpl.startRefresh(text)
    startRefreshTracker.produce(text)
  }

  override fun stopRefresh() {
    viewImpl.stopRefresh()
    stopRefreshTracker.produce(null)
  }

  override fun showNoDeviceScreen() {
    viewImpl.showNoDeviceScreen()
  }

  override fun showActiveDeviceScreen() {
    viewImpl.showActiveDeviceScreen()
  }

  override fun reportErrorRelatedToService(service: DeviceListService, message: String, t: Throwable) {
    reportErrorRelatedToServiceTracker.produce(message + getThrowableMessage(t))
    viewImpl.reportErrorRelatedToService(service, message, t)
  }


  override fun reportErrorGeneric(message: String, t: Throwable) {
    viewImpl.reportErrorGeneric(message, t)
  }

  override fun reportMessageRelatedToDevice(fileSystem: Device, message: String) {
    reportErrorRelatedToDeviceTracker.produce(message)
    viewImpl.reportMessageRelatedToDevice(fileSystem, message)
  }

  override fun expandNode(treeNode: ProcessTreeNode) {}

  fun killNodes(processList: List<ProcessTreeNode>) {
    val menuItem = ForceStopMenuItem(viewImpl, MenuContext.Popup)
    menuItem.run(processList)
  }

  val deviceCombo: JComboBox<Device?>
    get() = viewImpl.getDeviceCombo()


  val tree: Tree
    get() = viewImpl.getTree()

  private fun getThrowableMessage(t: Throwable): String =
    if (t.message == null) "" else ": ${t.message}"
  }