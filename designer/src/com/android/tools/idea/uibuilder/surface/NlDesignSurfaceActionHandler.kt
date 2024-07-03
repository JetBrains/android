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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor

class NlDesignSurfaceActionHandler
@JvmOverloads
constructor(
  surface: DesignSurface<*>,
  @VisibleForTesting copyPasteManager: CopyPasteManager = CopyPasteManager.getInstance(),
) : DesignSurfaceActionHandler(surface, copyPasteManager) {

  override fun deleteElement(dataContext: DataContext) {
    // For layout editor we may delete selected constraints.
    if (ConstraintComponentUtilities.clearSelectedConstraint(mySurface)) {
      return
    }
    super.deleteElement(dataContext)
  }

  override val flavor: DataFlavor = ItemTransferable.DESIGNER_FLAVOR

  override val pasteTarget: NlComponent?
    get() {
      val sceneView = mySurface.focusedSceneView ?: return null

      val selection = mySurface.selectionModel.selection
      if (selection.size > 1) {
        return null
      }
      var receiver: NlComponent? = if (selection.isNotEmpty()) selection[0] else null

      if (receiver == null) {
        // In the case where there is no selection but we only have a root component, use that one
        val components = sceneView.sceneManager.model.treeReader.components
        if (components.size == 1) {
          receiver = components[0]
        }
      }
      return receiver
    }

  override fun canHandleChildren(component: NlComponent, pasted: List<NlComponent>): Boolean {
    val handlerManager = ViewHandlerManager.get(component.model.project)
    val handler = handlerManager.getHandler(component) {}
    return handler is ViewGroupHandler
  }
}
