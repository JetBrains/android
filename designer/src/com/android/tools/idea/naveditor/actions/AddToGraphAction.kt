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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.actionDestinationId
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.startDestinationId
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import kotlin.streams.toList

abstract class AddToGraphAction(
  protected val mySurface: NavDesignSurface,
  name: String
) : AnAction(name) {

  override fun actionPerformed(e: AnActionEvent?) {
    val currentNavigation = mySurface.currentNavigation
    val components = mySurface.selectionModel.selection.filter { it.isDestination && it.parent == currentNavigation }

    if (components.isEmpty()) {
      return
    }

    WriteCommandAction.runWriteCommandAction(mySurface.project, "Add to Nested Graph", null, Runnable {
      val graph = newParent()
      val ids = components.map { it.id }
      // Pick an arbitrary destination to be the start destination,
      // but give preference to destinations with incoming actions
      // TODO: invoke dialog to have user select the best start destination?
      var candidate = components[0].id

      // All actions that point to any component in this set should now point to the
      // new parent graph, unless they are children of an element in the set
      currentNavigation.children.filter { !ids.contains(it.id) && it != graph }
        .flatMap { it.flatten().toList() }
        .filter { it.isAction && ids.contains(it.actionDestinationId) }
        .forEach {
          candidate = it.actionDestinationId
          it.actionDestinationId = graph.id
        }

      graph.model.addComponents(components, graph, null, InsertType.MOVE_WITHIN, mySurface)
      if (graph.startDestinationId == null) {
        graph.startDestinationId = candidate
      }
      mySurface.selectionModel.setSelection(listOf(graph))

    }, mySurface.model!!.file)
  }

  protected abstract fun newParent(): NlComponent
}