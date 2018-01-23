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
package com.android.tools.idea.naveditor.property.inspector

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.*
import com.intellij.openapi.command.WriteCommandAction

object AddActionDialogHelper {
  fun addItem(existing: NlComponent?, parent: NlComponent, resourceResolver: ResourceResolver?, defaultsType: AddActionDialog.Defaults) {
    val addActionDialog = AddActionDialog(defaultsType, existing, parent, resourceResolver)

    if (addActionDialog.showAndGet()) {
      WriteCommandAction.runWriteCommandAction(
          null, {
        val realComponent = existing ?: addActionDialog.source.createAction()
        realComponent.actionDestinationId = addActionDialog.destination?.id
        realComponent.enterAnimation = addActionDialog.enterTransition
        realComponent.exitAnimation = addActionDialog.exitTransition
        realComponent.popUpTo = addActionDialog.popTo
        realComponent.inclusive = addActionDialog.isInclusive
        realComponent.singleTop = addActionDialog.isSingleTop
        realComponent.document = addActionDialog.isDocument
        realComponent.clearTask = addActionDialog.isClearTask
      }
      )
    }
  }
}