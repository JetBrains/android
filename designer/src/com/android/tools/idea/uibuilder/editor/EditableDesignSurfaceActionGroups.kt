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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.actions.PanSurfaceAction
import com.android.tools.adtui.actions.ZoomActualAction
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomResetAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * Actions for editable designer editor file types. Includes the [PanSurfaceAction] since it'll only work for that kind of files.
 */
class EditableDesignSurfaceActionGroups : EditorActionsToolbarActionGroups {

  override val zoomControlsGroup: ActionGroup
    get() = createZoomControlsGroup()

  override val zoomLabelGroup: ActionGroup
    get() = createZoomLabelGroup()

  override val otherGroups: List<ActionGroup>
    get() = listOf(
      DefaultActionGroup().apply {
        add(PanSurfaceAction)
      })
}

/**
 * Populates the most basic/common actions that can be used on the DesignSurface.
 */
class BasicDesignSurfaceActionGroups : EditorActionsToolbarActionGroups {

  override val zoomControlsGroup: ActionGroup
    get() = createZoomControlsGroup()

  override val zoomLabelGroup: ActionGroup
    get() = createZoomLabelGroup()
}

fun createZoomControlsGroup(): ActionGroup {
  return DefaultActionGroup().apply {
    add(ZoomInAction.getInstance())
    add(ZoomOutAction.getInstance())
    add(ZoomActualAction.getInstance())
    add(ZoomToFitAction.getInstance())
  }
}

fun createZoomLabelGroup(): ActionGroup {
  return DefaultActionGroup().apply {
    add(ZoomLabelAction)
    add(ZoomResetAction.getInstance())
  }
}