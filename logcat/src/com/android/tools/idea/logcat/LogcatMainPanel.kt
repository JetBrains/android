/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.android.tools.idea.ddms.DeviceContext
import com.intellij.openapi.project.Project
import com.intellij.util.ui.components.BorderLayoutPanel

/**
 * The top level Logcat panel.
 */
internal class LogcatMainPanel(project: Project, state: LogcatPanelConfig?)
  : BorderLayoutPanel(), SplittingTabsStateProvider {

  private val deviceContext = DeviceContext()

  private val headerPanel = LogcatHeaderPanel(project, deviceContext)

  init {
    // TODO(aalbert): Ideally, we would like to be able to select the connected device and client in the header from the `state` but this
    //  might be challenging both technically and from a UX perspective. Since, when restoring the state, the device/client might not be
    //  available.
    //  From a UX perspective, it's not clear what we should do in this case.
    //  From a technical standpoint, the current implementation that uses DevicePanel doesn't seem to be well suited for preselecting a
    //  device/client.
    addToTop(headerPanel)
  }

  override fun getState(): String = LogcatPanelConfig.toJson(
    LogcatPanelConfig(deviceContext.selectedDevice?.serialNumber, deviceContext.selectedClient?.clientData?.packageName))
}