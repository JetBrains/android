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
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import kotlin.streams.toList

abstract class AddToGraphAction(
  protected val mySurface: NavDesignSurface,
  private val components: List<NlComponent>,
  name: String
) : AnAction(name) {

  // TODO: Should we set the start action here?
  override fun actionPerformed(e: AnActionEvent?) {
    WriteCommandAction.runWriteCommandAction(
        null
    ) {
      val graph = newParent()
      val currentNavigation = mySurface.currentNavigation
      val ids = components.map { it.id }

      // All actions that point to any component in this set should now point to the
      // new parent graph, unless they are children of an element in the set
      currentNavigation.children.filter { !ids.contains(it.id) && it != graph }
        .flatMap { it.flatten().toList() }
        .filter { it.isAction && ids.contains(it.actionDestinationId) }
        .forEach { it.actionDestinationId = graph.id }

      graph.model.addComponents(components, graph, null, InsertType.MOVE_WITHIN, mySurface)
      mySurface.selectionModel.setSelection(listOf(graph))
    }
  }

  protected abstract fun newParent(): NlComponent
}