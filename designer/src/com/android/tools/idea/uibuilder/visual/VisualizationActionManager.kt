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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomShortcut
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.JComponent

class VisualizationActionManager(surface: NlDesignSurface) : NlActionManager(surface) {
  private val zoomInAction: AnAction = ZoomShortcut.ZOOM_IN.registerForAction(ZoomInAction, surface, surface)
  private val zoomOutAction: AnAction = ZoomShortcut.ZOOM_OUT.registerForAction(ZoomOutAction, surface, surface)
  private val zoomToFitAction: AnAction = ZoomShortcut.ZOOM_FIT.registerForAction(ZoomToFitAction, surface, surface)

  override fun registerActionsShortcuts(component: JComponent) = Unit

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(zoomInAction)
    group.add(zoomOutAction)
    group.add(zoomToFitAction)
    return group
  }

  override fun getToolbarActions(component: NlComponent?, newSelection: List<NlComponent>) = DefaultActionGroup()
}
