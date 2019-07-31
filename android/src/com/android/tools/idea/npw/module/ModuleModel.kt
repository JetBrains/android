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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File


abstract class ModuleModel(
  val project: Project,
  val templateHandle: TemplateHandle,
  private val projectSyncInvoker: ProjectSyncInvoker
) : WizardModel() {
  protected fun doDryRun(moduleRoot: File, templateValues: Map<String, Any>): Boolean =
    renderTemplate(true, project, moduleRoot, templateValues, null)

  protected fun render(moduleRoot: File, templateValues: Map<String, Any>) {
    val filesToOpen = mutableListOf<File>()
    val success = renderTemplate(false, project, moduleRoot, templateValues, filesToOpen)
    if (success) {
      // calling smartInvokeLater will make sure that files are open only when the project is ready
      DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, true) }
      projectSyncInvoker.syncProject(project)
    }
  }

  private fun renderTemplate(
    dryRun: Boolean, project: Project, moduleRoot: File, templateValues: Map<String, Any>, filesToOpen: MutableList<File>?
  ): Boolean {
    val template = templateHandle.template

    val context = Builder.newContext(template, project)
      .withCommandName(message("android.wizard.module.new.module.menu.description"))
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(moduleRoot)
      .withParams(templateValues)
      .intoOpenFiles(filesToOpen)
      .build()

    return template.render(context!!, dryRun)
  }
}