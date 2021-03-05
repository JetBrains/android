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
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType.BITMAP_AS_REQUESTED
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import icons.StudioIcons.LayoutInspector.MODE_3D
import icons.StudioIcons.LayoutInspector.RESET_VIEW

/** Creates the actions toolbar used on the [DeviceViewPanel] */
class DeviceViewPanelActionsToolbarProvider(
  deviceViewPanel: DeviceViewPanel,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbarProvider(deviceViewPanel, parentDisposable) {

  init {
    updateToolbar()
  }

  override fun getActionGroups() = LayoutInspectorToolbarGroups
}

object Toggle3dAction : AnAction(MODE_3D) {
  override fun actionPerformed(event: AnActionEvent) {
    val model = event.getData(DEVICE_VIEW_MODEL_KEY) ?: return
    if (model.isRotated) {
      model.resetRotation()
    }
    else {
      model.rotate(0.45, 0.06)
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val model = event.getData(DEVICE_VIEW_MODEL_KEY)
    val client = LayoutInspector.get(event)?.currentClient
    event.presentation.icon = if (model?.isRotated == true) RESET_VIEW else MODE_3D
    if (model != null && model.overlay == null &&
        client?.capabilities?.contains(InspectorClient.Capability.SUPPORTS_SKP) == true) {
      event.presentation.isEnabled = true
      event.presentation.text = if (model.isRotated) "Reset View" else "Rotate View"
    }
    else {
      event.presentation.isEnabled = false
      val isLowerThenApi29 = client != null && client.isConnected && client.process.device.apiLevel < 29
      event.presentation.text =
        when {
          model?.overlay != null -> "Rotation not available when overlay is active"
          model?.pictureType == BITMAP_AS_REQUESTED -> "No compatible renderer found for device image, rotation not available"
          isLowerThenApi29 -> "Rotation not available for devices below API 29"
          else -> "Error while rendering device image, rotation not available"
        }
    }
  }
}

object LayoutInspectorToolbarGroups : EditorActionsToolbarActionGroups {
  override val zoomLabelGroup = DefaultActionGroup().apply {
    add(ZoomLabelAction)
    add(ZoomResetAction())
  }

  override val otherGroups: List<ActionGroup> = listOf(DefaultActionGroup().apply { add(PanSurfaceAction) },
                                                       DefaultActionGroup().apply { add(Toggle3dAction) })

  override val zoomControlsGroup = DefaultActionGroup().apply {
    add(ZoomInAction())
    add(ZoomOutAction())
    add(ZoomToFitAction())
  }
}