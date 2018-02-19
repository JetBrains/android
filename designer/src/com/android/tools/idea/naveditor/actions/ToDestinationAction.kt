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

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.property.inspector.AddActionDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ToDestinationAction(
  private val surface: DesignSurface,
  private val component: NlComponent,
  private val resourceResolver: ResourceResolver
) :
    AnAction("To Destination..") {
  override fun actionPerformed(e: AnActionEvent?) {
    val addActionDialog = AddActionDialog(AddActionDialog.Defaults.NORMAL, null, component, resourceResolver)
    if (addActionDialog.showAndGet()) {
      val action = addActionDialog.writeUpdatedAction()
      surface.selectionModel.setSelection(listOf(action))
    }
  }
}