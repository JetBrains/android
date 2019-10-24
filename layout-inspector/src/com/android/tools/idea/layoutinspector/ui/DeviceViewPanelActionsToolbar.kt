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
import com.android.tools.editor.EditorActionsFloatingToolbar
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import icons.StudioIcons

/** Creates the actions toolbar used on the [DeviceViewPanel] */
class DeviceViewPanelActionsToolbar(
  private val deviceViewPanel: DeviceViewPanel,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbar(deviceViewPanel, parentDisposable) {

  init {
    updateToolbar()
  }

  override fun getActionGroups() = LayoutInspectorToolbarGroups
}

object RecenterAction : AnAction(StudioIcons.LayoutEditor.Motion.BASE_LAYOUT) { // TODO: replace icon
  override fun actionPerformed(event: AnActionEvent) {
    event.getData(DEVICE_VIEW_MODEL_KEY)?.resetRotation()
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = event.getData(DEVICE_VIEW_MODEL_KEY)?.isRotated ?: false
  }
}

object LayoutInspectorToolbarGroups : EditorActionsToolbarActionGroups {
  override val zoomLabelGroup = DefaultActionGroup().apply {
    add(ZoomLabelAction)
    add(ZoomResetAction)
  }

  override val otherGroups: List<ActionGroup> = listOf(DefaultActionGroup().apply { add(PanSurfaceAction) },
                                                       DefaultActionGroup().apply { add(RecenterAction) })

  override val zoomControlsGroup = DefaultActionGroup().apply {
    add(ZoomOutAction)
    add(ZoomInAction)
    add(ZoomToFitAction)
  }
}