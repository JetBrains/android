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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.observable.core.StringValueProperty
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
  private val projectSyncInvoker: ProjectSyncInvoker,
  moduleName: String
) : WizardModel() {
  @JvmField val moduleName = StringValueProperty(moduleName)
  val templateValues = mutableMapOf<String, Any>()
  private val multiTemplateRenderer = MultiTemplateRenderer { renderer ->
    renderer(project)
    projectSyncInvoker.syncProject(project)
  }
  protected abstract val renderer: MultiTemplateRenderer.TemplateRenderer

  public override fun handleFinished() {
    multiTemplateRenderer.requestRender(renderer)
  }

  override fun handleSkipped() {
    multiTemplateRenderer.skipRender()
  }

  protected abstract inner class ModuleTemplateRenderer : MultiTemplateRenderer.TemplateRenderer {
    @WorkerThread
    override fun doDryRun(): Boolean = renderTemplate(true, project)

    @WorkerThread
    override fun render() {
      renderTemplate(false, project)
    }

    private fun renderTemplate(dryRun: Boolean, project: Project, runFromTemplateRenderer: Boolean = false): Boolean {
      val projectRoot = File(project.basePath!!)
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())
      val template = templateHandle.template
      val filesToOpen = mutableListOf<File>()

      val context = Builder.newContext(template, project)
        .withCommandName(message("android.wizard.module.new.module.command"))
        .withDryRun(dryRun)
        .withShowErrors(true)
        .withOutputRoot(projectRoot)
        .withModuleRoot(moduleRoot)
        .withParams(templateValues)
        .intoOpenFiles(filesToOpen)
        .build()

      return template.render(context!!, dryRun).also {
        if (it && !dryRun) {
          // calling smartInvokeLater will make sure that files are open only when the project is ready
          DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, true) }
          // TODO remove after moving to moduleTemplateRenderer
          if (!runFromTemplateRenderer) {
            projectSyncInvoker.syncProject(project)
          }
        }
      }
    }
  }
}

/**
 * Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
 * the Module Path (called Project Path in gradle world) in "settings.gradle"
 */
fun getModuleRoot(projectLocation: String, moduleName: String) = File(projectLocation, moduleName.replace(':', File.separatorChar))
