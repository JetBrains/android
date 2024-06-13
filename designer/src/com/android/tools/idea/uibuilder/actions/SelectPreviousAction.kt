/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class SelectPreviousAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) ?: return
    val selectable = surface.selectableComponents
    if (selectable.isEmpty()) {
      return
    }

    val selectionModel = surface.selectionModel
    val selection = selectionModel.selection

    val previous =
      if (selection.size == 1) {
        val index = Math.max(selectable.indexOf(selection[0]), 0)
        selectable[(index - 1 + selectable.size) % selectable.size]
      } else {
        selectable.last()
      }

    selectionModel.setSelection(listOf(previous))
    surface.repaint()
  }
}
