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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Component
import java.awt.event.MouseEvent

/** A gear action that when clicked shows a popup containing [actions]. */
class GearAction(vararg val actions: AnAction) :
  DumbAwareAction("More Options", null, AllIcons.General.GearPlain) {
  override fun actionPerformed(event: AnActionEvent) {
    var x = 0
    var y = 0
    val inputEvent = event.inputEvent
    if (inputEvent is MouseEvent) {
      x = inputEvent.x
      y = inputEvent.y
    }
    showGearPopup(inputEvent!!.component, x, y, actions.toList())
  }
}

private fun showGearPopup(component: Component, x: Int, y: Int, actions: List<AnAction>) {
  val group = DefaultActionGroup()
  actions.forEach { group.add(it) }
  val popupMenu =
    ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
  popupMenu.component.show(component, x, y)
}
