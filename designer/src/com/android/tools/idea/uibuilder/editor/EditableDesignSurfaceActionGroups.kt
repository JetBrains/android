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
import com.android.tools.adtui.actions.ZoomShortcut
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.JComponent

/**
 * Actions for editable designer editor file types. Includes the [PanSurfaceAction] since it'll only work for that kind of files.
 */
class EditableDesignSurfaceActionGroups(private val shortcutConsumer: JComponent) : EditorActionsToolbarActionGroups {

  override val zoomControlsGroup: ActionGroup
    get() = createZoomControlsGroup(shortcutConsumer, this)

  override val zoomLabelGroup: ActionGroup
    get() = createZoomLabelGroup()

  override val panControlsGroup: ActionGroup
    get() = DefaultActionGroup().apply {
      add(PanSurfaceAction)
    }
}

/**
 * Populates the most basic/common actions that can be used on the DesignSurface.
 */
class BasicDesignSurfaceActionGroups(private val shortcutConsumer: JComponent) : EditorActionsToolbarActionGroups {

  override val zoomControlsGroup: ActionGroup
    get() = createZoomControlsGroup(shortcutConsumer, this)

  override val zoomLabelGroup: ActionGroup
    get() = createZoomLabelGroup()

  override val panControlsGroup: ActionGroup
    get() = DefaultActionGroup.EMPTY_GROUP
}

private fun createZoomControlsGroup(shortcutConsumer: JComponent, parentDisposable: Disposable) =
  DefaultActionGroup().apply {
    add(ZoomShortcut.ZOOM_IN.registerForAction(ZoomInAction, shortcutConsumer, parentDisposable))
    add(ZoomShortcut.ZOOM_OUT.registerForAction(ZoomOutAction, shortcutConsumer, parentDisposable))
    add(ZoomShortcut.ZOOM_ACTUAL.registerForAction(ZoomActualAction, shortcutConsumer, parentDisposable))
    add(ZoomShortcut.ZOOM_FIT.registerForAction(ZoomToFitAction, shortcutConsumer, parentDisposable))
  }

private fun createZoomLabelGroup() =
  DefaultActionGroup().apply {
    add(ZoomLabelAction)
    add(ZoomResetAction)
  }