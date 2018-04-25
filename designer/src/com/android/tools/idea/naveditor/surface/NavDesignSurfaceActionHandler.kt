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

import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.naveditor.model.actionDestination
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import java.util.stream.Collectors

class NavDesignSurfaceActionHandler(val surface: NavDesignSurface) : DesignSurfaceActionHandler(surface) {
  override fun deleteElement(dataContext: DataContext) {
    val superCall = { super.deleteElement(dataContext) }
    val action = object: WriteCommandAction<Unit>(surface.project, "Delete Component", surface.model!!.file) {
      override fun run(result: Result<Unit>) {
        val model = surface.model ?: return
        val selectionModel = surface.selectionModel
        for (component in selectionModel.selection) {
          if (component.isDestination) {
            val parent = component.parent ?: continue
            model.delete(parent.flatten().filter { it.isAction && it.actionDestination == component }.collect(Collectors.toList()))
          }
        }
        superCall()
      }
    }
    action.execute()
  }
}