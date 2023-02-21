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

import com.android.tools.adtui.actions.PanSurfaceAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomResetAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.editor.EditorActionsFloatingToolbarProvider
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.android.tools.idea.layoutinspector.ui.DeviceViewPanel
import com.android.tools.idea.layoutinspector.ui.Toggle3dAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton

/** Creates the actions toolbar used on the [DeviceViewPanel] */
class FloatingToolbarProvider(
  deviceViewPanel: DeviceViewPanel,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbarProvider(deviceViewPanel, parentDisposable) {

  /** Defines the groups of actions shown in the floating toolbar */
  private val actionGroup = object : EditorActionsToolbarActionGroups {
    override val zoomLabelGroup = DefaultActionGroup().apply {
      add(ZoomLabelAction)
      add(ZoomResetAction)
    }

    override val zoomControlsGroup = DefaultActionGroup().apply {
      add(ZoomInAction.getInstance())
      add(ZoomOutAction.getInstance())
      add(ZoomToFitAction.getInstance())
    }

    val panSurfaceGroup = DefaultActionGroup().apply { add(PanSurfaceAction) }
    val toggle3dGroup = DefaultActionGroup().apply { add(Toggle3dAction) }

    override val otherGroups: List<ActionGroup> = listOf(
      panSurfaceGroup,
      toggle3dGroup
    )
  }

  val toggle3dActionButton: ActionButton? get() = findActionButton(actionGroup.toggle3dGroup, Toggle3dAction)

  init {
    updateToolbar()
  }

  override fun getActionGroups() = actionGroup
}