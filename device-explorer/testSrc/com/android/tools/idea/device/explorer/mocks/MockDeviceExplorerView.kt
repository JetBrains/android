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
package com.android.tools.idea.device.explorer.mocks

import com.android.tools.idea.device.explorer.DeviceExplorerModel
import com.android.tools.idea.device.explorer.ui.DeviceExplorerView
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewImpl
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewListener
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class MockDeviceExplorerView(project: Project, model: DeviceExplorerModel) : DeviceExplorerView {
  private val viewImpl = DeviceExplorerViewImpl(project, model, "TEST_ID")

  fun viewComboBox() = viewImpl.getDeviceCombo()
  fun viewTabPane() = viewImpl.getTabPane()

  override fun setup() {
    viewImpl.setup()
  }

  override fun addListener(listener: DeviceExplorerViewListener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: DeviceExplorerViewListener) {
    viewImpl.removeListener(listener)
  }

  override fun addTab(tab: JComponent, title: String) {
    viewImpl.addTab(tab, title)
  }

  override suspend fun trackDeviceListChanges() {
    viewImpl.trackDeviceListChanges()
  }

  override suspend fun trackActiveDeviceChanges() {
    viewImpl.trackActiveDeviceChanges()
  }

  override fun reportErrorGeneric(message: String, t: Throwable) {}
}