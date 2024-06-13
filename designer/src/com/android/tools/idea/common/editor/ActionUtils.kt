/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("ActionUtils")

package com.android.tools.idea.common.editor

import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Helper function to show the popup menu in [DesignSurface]. The popup menu will appear at the
 * mouse position of [event]. If the source of mouse event is not a [Component] then the popup
 * appears at the given [DesignSurface]. If the given [group] is empty, then nothing happens. The
 * [place] describes the place of popup action, which is passed into
 * [com.intellij.openapi.actionSystem.AnActionEvent] when
 * [com.intellij.openapi.actionSystem.AnAction] is updated or performed. See
 * [ActionManager.createActionPopupMenu] and [com.intellij.openapi.actionSystem.ActionPlaces] for
 * more information.
 */
fun DesignSurface<*>.showPopup(event: MouseEvent, group: ActionGroup, place: String) {
  val invoker = if (event.source is Component) event.source as Component else this
  showPopup(this, invoker, event.x, event.y, group, place)
}

/**
 * Show the popup for the [invoker]. The popup menu will appear at ([x], [y]) position in
 * [invoker]'s coordinate system. If the given [group] is empty, then nothing happens. The [place]
 * describes the place of popup action, which is passed into
 * [com.intellij.openapi.actionSystem.AnActionEvent] when
 * [com.intellij.openapi.actionSystem.AnAction] is updated or performed. See
 * [ActionManager.createActionPopupMenu] and [com.intellij.openapi.actionSystem.ActionPlaces] for
 * more information.
 */
fun showPopup(
  surface: DesignSurface<*>?,
  invoker: Component,
  x: Int,
  y: Int,
  group: ActionGroup,
  place: String,
) {
  if (group.getChildren(null).isEmpty()) {
    return
  }
  val actionManager = ActionManager.getInstance()
  // TODO (b/151315668): Should the place be ActionPlaces.POPUP?
  val popupMenu = actionManager.createActionPopupMenu(place, group)
  surface?.let { popupMenu.setTargetComponent(it) }
  popupMenu.component.show(invoker, x, y)
}
