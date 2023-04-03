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
package com.android.tools.idea.layoutinspector.ui.toolbar

import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.snapshots.SnapshotAction
import com.android.tools.idea.layoutinspector.ui.AlphaSliderAction
import com.android.tools.idea.layoutinspector.ui.DEVICE_VIEW_MODEL_KEY
import com.android.tools.idea.layoutinspector.ui.LayerSpacingSliderAction
import com.android.tools.idea.layoutinspector.ui.RefreshAction
import com.android.tools.idea.layoutinspector.ui.ToggleOverlayAction
import com.android.tools.idea.layoutinspector.ui.ViewMenuAction
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import icons.StudioIcons
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle
import javax.swing.JComponent

const val LAYOUT_INSPECTOR_MAIN_TOOLBAR = "LayoutInspector.MainToolbar"

/**
 * Creates the main toolbar used by Layout Inspector.
 */
fun createLayoutInspectorMainToolbar(
  targetComponent: JComponent,
  layoutInspector: LayoutInspector,
  selectProcessAction: AnAction?
): ActionToolbar {
  val actionGroup = LayoutInspectorActionGroup(layoutInspector, selectProcessAction)
  val actionToolbar = ActionManager.getInstance().createActionToolbar(LAYOUT_INSPECTOR_MAIN_TOOLBAR, actionGroup, true)
  ActionToolbarUtil.makeToolbarNavigable(actionToolbar)
  actionToolbar.component.name = LAYOUT_INSPECTOR_MAIN_TOOLBAR
  actionToolbar.component.putClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY, true)
  actionToolbar.targetComponent = targetComponent
  actionToolbar.updateActionsImmediately()
  return actionToolbar
}

/**
 * Action Group containing all the actions used in Layout Inspector's main toolbar.
 */
private class LayoutInspectorActionGroup(layoutInspector: LayoutInspector, selectProcessAction: AnAction?) : DefaultActionGroup() {
  init {
    if (selectProcessAction != null) {
      add(selectProcessAction)
    }
    add(Separator.getInstance())
    add(ViewMenuAction)
    add(ToggleOverlayAction)
    if (!layoutInspector.isSnapshot) {
      add(SnapshotAction)
    }
    add(AlphaSliderAction)
    if (!layoutInspector.isSnapshot) {
      add(Separator.getInstance())
      add(ToggleLiveUpdatesAction(layoutInspector))
      add(RefreshAction)
    }
    add(Separator.getInstance())
    add(LayerSpacingSliderAction)
  }
}

/**
 * Action used to Toggle Live Updates on/off.
 */
class ToggleLiveUpdatesAction(
  private val layoutInspector: LayoutInspector,
) : ToggleAction({ "Live Updates" }, StudioIcons.LayoutInspector.LIVE_UPDATES), TooltipDescriptionProvider, TooltipLinkProvider {

  override fun update(event: AnActionEvent) {
    val currentClient = client(event)

    val isLiveInspector = !currentClient.isConnected ||
                          currentClient.capabilities.contains(InspectorClient.Capability.SUPPORTS_CONTINUOUS_MODE)
    val isLowerThenApi29 = currentClient.isConnected && currentClient.process.device.apiLevel < 29

    event.presentation.isEnabled = isLiveInspector || !currentClient.isConnected
    super.update(event)
    event.presentation.description = when {
      isLowerThenApi29 -> "Live updates not available for devices below API 29"
      !isLiveInspector -> AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY)
      else -> "Stream updates to your app's layout from your device in realtime. Enabling live updates consumes more device " +
              "resources and might impact runtime performance."
    }
  }

  @Suppress("DialogTitleCapitalization")
  override fun getTooltipLink(owner: JComponent?) = TooltipLinkProvider.TooltipLink("Learn More") {
    BrowserUtil.browse("https://d.android.com/r/studio-ui/layout-inspector-live-updates")
  }

  // When disconnected: display the default value after the inspector is connected to the device.
  override fun isSelected(event: AnActionEvent): Boolean {
    return layoutInspector.inspectorClientSettings.isCapturingModeOn
  }

  override fun setSelected(event: AnActionEvent, state: Boolean) {
    event.getData(DEVICE_VIEW_MODEL_KEY)?.fireModified()
    val currentClient = client(event)
    if (currentClient.capabilities.contains(InspectorClient.Capability.SUPPORTS_CONTINUOUS_MODE)) {
      when (state) {
        true -> layoutInspector.coroutineScope.launch { currentClient.startFetching() }
        false -> layoutInspector.coroutineScope.launch { currentClient.stopFetching() }
      }
    }
    layoutInspector.inspectorClientSettings.isCapturingModeOn = state
  }

  private fun client(event: AnActionEvent): InspectorClient =
    LayoutInspector.get(event)?.currentClient ?: DisconnectedClient
}