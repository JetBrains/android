/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.resources.ResourceType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.android.actions.CreateXmlResourceDialog
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidResourceUtil
import org.jetbrains.android.util.AndroidUtils

/**
 * [AnAction] wrapper that calls the [CreateXmlResourceDialog] to create new resources in a project.
 */
class NewResourceValueAction(
  private val type: ResourceType,
  private val facet: AndroidFacet,
  private val createdResourceCallback: (String, ResourceType) -> Unit
): AnAction("${type.displayName} Value") {

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = CreateXmlResourceDialog(facet.module,
                                         type,
                                         null,
                                         null,
                                         true,
                                         null,
                                         null)
    dialog.title = "New ${type.displayName} Value"
    if (!dialog.showAndGet()) return
    val module = facet.module
    val project = module.project
    val resDir = dialog.resourceDirectory
    if (resDir == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("check.resource.dir.error", module))
      return
    }
    val fileName = dialog.fileName
    val dirNames = dialog.dirNames
    val resValue = dialog.value
    val resName = dialog.resourceName
    if (!AndroidResourceUtil.createValueResource(project, resDir, resName, type, fileName, dirNames, resValue)) {
      return
    }
    PsiDocumentManager.getInstance(module.project).commitAllDocuments()

    // Show/open/select created resource.
    createdResourceCallback(resName, type)
  }
}