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
package com.android.tools.idea.layoutinspector.runningdevices.actions

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManagerGlobalState
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.settings.STUDIO_RELEASE_NOTES_EMBEDDED_LI_URL
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.streaming.core.DEVICE_ID_KEY
import com.android.tools.idea.streaming.core.DISPLAY_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import icons.StudioIcons
import javax.swing.JComponent

/** Action used to turn Layout Inspector on and off in Running Devices tool window. */

/**
 * The min API supported by the Embedded Layout Inspector. Before API 29 we don't have support for
 * live updates.
 */
const val EMBEDDED_LAYOUT_INSPECTOR_MIN_API = 29

class ToggleLayoutInspectorAction :
  ToggleAction(
    "Toggle Layout Inspector",
    "Toggles Layout Inspection on and off for this device.",
    StudioIcons.Shell.ToolWindows.CAPTURES,
  ),
  TooltipDescriptionProvider,
  TooltipLinkProvider {

  private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

  override fun isSelected(e: AnActionEvent): Boolean {
    if (!LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) {
      return false
    }

    val project = e.project ?: return false
    val deviceId = DEVICE_ID_KEY.getData(e.dataContext) ?: return false

    return LayoutInspectorManager.getInstance(project).isEnabled(deviceId)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) {
      return
    }

    val project = e.project ?: return
    val deviceId = DEVICE_ID_KEY.getData(e.dataContext) ?: return

    LayoutInspectorManager.getInstance(project).enableLayoutInspector(deviceId, state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project ?: return
    val deviceId = DEVICE_ID_KEY.getData(e.dataContext) ?: return
    val isEnabled = LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled
    e.presentation.isVisible =
      isEnabled && LayoutInspectorManager.getInstance(project).isSupported(deviceId)

    val displayView = DISPLAY_VIEW_KEY.getData(e.dataContext)
    val apiLevel = runCatching { displayView?.apiLevel }.getOrNull()
    val serialNumber = runCatching { displayView?.deviceSerialNumber }.getOrNull()
    val isEmulator = displayView is EmulatorView

    if (apiLevel == null || serialNumber == null) {
      e.presentation.isEnabled = false
    } else if (apiLevel < EMBEDDED_LAYOUT_INSPECTOR_MIN_API) {
      // We decided to always have Live Updates ON in Embedded Layout Inspector.
      // Live updates requires API 29+.
      // TODO(b/285889090): provide a better experience for devices with API lower than 29.
      e.presentation.isEnabled = false
      e.presentation.description = LayoutInspectorBundle.message("api.29.limit")
    } else {
      e.presentation.isEnabled = true
      e.presentation.description = ""
    }

    if (e.presentation.isVisible && e.presentation.isEnabled) {
      // Do this check only if the toggle button is not already disabled.
      enforceOnlyOneLayoutInspectorPerDeviceAcrossProjects(e)
    }
  }

  /**
   * Checks if Layout Inspector is active for the current tab, across projects. If yes, disables the
   * toggle action for the tab in the projects where Layout Inspector is not enabled. This is to
   * avoid multiple projects trying to connect Layout Inspector to the same process at the same
   * time, which is not a supported use case.
   */
  private fun enforceOnlyOneLayoutInspectorPerDeviceAcrossProjects(e: AnActionEvent) {
    val project = e.project ?: return
    val deviceId = DEVICE_ID_KEY.getData(e.dataContext) ?: return
    val isLayoutInspectorEnabledForTab =
      LayoutInspectorManager.getInstance(project).isEnabled(deviceId)
    if (
      !isLayoutInspectorEnabledForTab &&
        LayoutInspectorManagerGlobalState.tabsWithLayoutInspector.contains(deviceId)
    ) {
      // Disable the toggle button if Layout Inspector is already active for this device (across
      // multiple projects), except for the tab in the project where Layout Inspector is already
      // active (the user needs to have the option to toggle Layout Inspector off).
      e.presentation.isEnabled = false
      e.presentation.description =
        LayoutInspectorBundle.message("layout.inspector.active.in.another.project")
    }
  }

  @Suppress("DialogTitleCapitalization")
  override fun getTooltipLink(owner: JComponent?): TooltipLinkProvider.TooltipLink {
    return TooltipLinkProvider.TooltipLink(LayoutInspectorBundle.message("learn.more")) {
      BrowserUtil.browse(STUDIO_RELEASE_NOTES_EMBEDDED_LI_URL)
    }
  }
}
