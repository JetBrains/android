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
package com.android.tools.idea.naveditor.surface

import com.android.SdkConstants
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.supportsActions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import java.awt.datatransfer.DataFlavor
import java.util.stream.Collectors

class NavDesignSurfaceActionHandler(val surface: NavDesignSurface) : DesignSurfaceActionHandler(surface) {

  override val flavor: DataFlavor = ItemTransferable.NAV_FLAVOR

  override fun deleteElement(dataContext: DataContext) {
    val superCall = { super.deleteElement(dataContext) }

    val nextSelection = nextSelection()
    val selection = surface.selectionModel.selection

    WriteCommandAction.runWriteCommandAction(surface.project, "Delete Component", null, {
      val model = surface.model
      if (model != null) {
        for (component in selection) {
          if (component.isDestination) {
            surface.getSceneManager(model)?.performUndoablePositionAction(component)
            val parent = component.parent ?: continue
            model.treeWriter.delete(parent.flatten().filter { it.isAction && it.actionDestination == component }.collect(Collectors.toList()))
            if (component.isStartDestination) {
              parent.removeAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
            }
          }
        }
      }
      superCall()
    }, surface.model!!.file)
    surface.selectionModel.setSelection(listOf(nextSelection))
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    return super.canDeleteElement(dataContext) &&
           !mySurface.selectionModel.selection.contains(surface.currentNavigation)
  }

  override fun isCutEnabled(dataContext: DataContext): Boolean {
    return super.isCutEnabled(dataContext) &&
           !mySurface.selectionModel.selection.contains(surface.currentNavigation)
  }

  override val pasteTarget: NlComponent
    get() {
      val selection = mySurface.selectionModel.selection
      return if (selection.size == 1) selection[0] else surface.currentNavigation
    }

  override fun canHandleChildren(component: NlComponent, pasted: List<NlComponent>): Boolean {
    // Actions can be children of anything selectable. Destinations are children of navigations, but won't get pasted into a subnav
    // that's selected (unless it's also the current nav).
    return (pasted.all { it.isAction } && component.supportsActions) ||
           component == surface.currentNavigation
  }

  // Determine the next component to be selected after the current selection is deleted
  // Returns the root if multiple items are selected
  private fun nextSelection(): NlComponent {
    val selection = surface.selectionModel.selection
    if (selection.size != 1) {
      return surface.currentNavigation
    }

    val current = selection[0]
    val selectable = surface.selectableComponents
    if (selectable.size == 0) {
      return surface.currentNavigation
    }

    var index = selectable.indexOf(current) + 1

    if (current.isDestination) {
      while (index < selectable.size && !selectable[index].isDestination) {
        index++
      }
    }

    return selectable[index % selectable.size]
  }
}