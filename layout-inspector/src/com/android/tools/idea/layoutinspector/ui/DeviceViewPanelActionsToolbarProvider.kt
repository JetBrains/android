/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.actions.PanSurfaceAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomResetAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.editor.EditorActionsFloatingToolbarProvider
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.actionSystem.impl.ActionButton
import icons.StudioIcons.LayoutInspector.MODE_3D
import icons.StudioIcons.LayoutInspector.RESET_VIEW
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

val TOGGLE_3D_ACTION_BUTTON_KEY = DataKey.create<ActionButton?>("$DEVICE_VIEW_ACTION_TOOLBAR_NAME.FloatingToolbar")

private const val ROTATION_FRAMES = 20L
private const val ROTATION_TIMEOUT = 10_000L

/** Creates the actions toolbar used on the [DeviceViewPanel] */
class DeviceViewPanelActionsToolbarProvider(
  deviceViewPanel: DeviceViewPanel,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbarProvider(deviceViewPanel, parentDisposable) {

  val toggle3dActionButton: ActionButton?
    get() = findActionButton(LayoutInspectorToolbarGroups.toggle3dGroup, Toggle3dAction)

  init {
    updateToolbar()
  }

  override fun getActionGroups() = LayoutInspectorToolbarGroups
}

object Toggle3dAction : AnAction(MODE_3D), TooltipLinkProvider, TooltipDescriptionProvider {
  @VisibleForTesting
  var executorFactory = { Executors.newSingleThreadScheduledExecutor() }
  @VisibleForTesting
  var getCurrentTimeMillis = { System.currentTimeMillis() }

  override fun actionPerformed(event: AnActionEvent) {
    val model = event.getData(DEVICE_VIEW_MODEL_KEY) ?: return
    val inspector = LayoutInspector.get(event)
    val client = inspector?.currentClient

    if (model.isRotated) {
      model.resetRotation()
    }
    else {
      client?.updateScreenshotType(AndroidWindow.ImageType.SKP, -1f)
      val timerStart = getCurrentTimeMillis()
      val executor = executorFactory()
      var iteration = 0
      executor.scheduleAtFixedRate(
        {
          val currentTime = getCurrentTimeMillis()
          if (currentTime - timerStart > ROTATION_TIMEOUT) {
            // We weren't able to get the SKP in a reasonable amount of time, so stop waiting.
            executor.shutdown()
            return@scheduleAtFixedRate
          }
          // Don't rotate or start the rotation timeout if we haven't received an SKP yet.
          val inspectorModel = inspector?.layoutInspectorModel
          // Wait until we have an actual SKP (not pending)
          if (inspectorModel?.pictureType != AndroidWindow.ImageType.SKP) {
            return@scheduleAtFixedRate
          }
          iteration++
          if (iteration > ROTATION_FRAMES) {
            executor.shutdown()
            return@scheduleAtFixedRate
          }
          model.xOff = iteration * 0.45 / ROTATION_FRAMES
          model.yOff = iteration * 0.06 / ROTATION_FRAMES
          model.refresh()
        }, 0, 15, TimeUnit.MILLISECONDS)
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val model = event.getData(DEVICE_VIEW_MODEL_KEY)
    val inspector = LayoutInspector.get(event)
    val client = inspector?.currentClient
    val inspectorModel = inspector?.layoutInspectorModel
    event.presentation.icon = if (model?.isRotated == true) RESET_VIEW else MODE_3D
    if (model != null && model.overlay == null &&
        client?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) == true &&
        (client.isCapturing || inspectorModel?.pictureType == AndroidWindow.ImageType.SKP)) {
      event.presentation.isEnabled = true
      if (model.isRotated) {
        event.presentation.text = "2D Mode"
        event.presentation.description =
          "Inspect the layout in 2D mode. Enabling this mode has less impact on your device's runtime performance."
      }
      else {
        event.presentation.text = "3D Mode"
        event.presentation.description =
          "Visually inspect the hierarchy by clicking and dragging to rotate the layout. Enabling this mode consumes more device " +
          "resources and might impact runtime performance."
      }
    }
    else {
      event.presentation.isEnabled = false
      val isLowerThenApi29 = client != null && client.isConnected && client.process.device.apiLevel < 29
      @Suppress("DialogTitleCapitalization")
      event.presentation.text =
        when {
          model?.overlay != null -> "Rotation not available when overlay is active"
          isLowerThenApi29 -> "Rotation not available for devices below API 29"
          else -> "Error while rendering device image, rotation not available"
        }
    }
  }

  @Suppress("DialogTitleCapitalization")
  override fun getTooltipLink(owner: JComponent?) = TooltipLinkProvider.TooltipLink("Learn More") {
    // TODO: link for performance issue
    BrowserUtil.browse("https://d.android.com/r/studio-ui/layout-inspector-2D-3D-mode")
  }
}

object LayoutInspectorToolbarGroups : EditorActionsToolbarActionGroups {
  override val zoomLabelGroup = DefaultActionGroup().apply {
    add(ZoomLabelAction)
    add(ZoomResetAction)
  }

  val toggle3dGroup = DefaultActionGroup().apply { add(Toggle3dAction) }

  override val otherGroups: List<ActionGroup> = listOf(DefaultActionGroup().apply { add(PanSurfaceAction) }, toggle3dGroup)

  override val zoomControlsGroup = DefaultActionGroup().apply {
    add(ZoomInAction.getInstance())
    add(ZoomOutAction.getInstance())
    add(ZoomToFitAction.getInstance())
  }
}