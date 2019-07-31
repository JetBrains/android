/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.java

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File

class NewJavaModuleModel(val project: Project,
                         private val myTemplateHandle: TemplateHandle,
                         private val myProjectSyncInvoker: ProjectSyncInvoker) : WizardModel() {
  @JvmField val libraryName: StringProperty = StringValueProperty("lib")
  @JvmField val packageName: StringProperty = StringValueProperty()
  @JvmField val className: StringProperty = StringValueProperty("MyClass")

  override fun handleFinished() {
    val modulePaths = createDefaultTemplateAt(project.basePath!!, libraryName.get()).paths
    val templateValues = mutableMapOf<String, Any>()
    TemplateValueInjector(templateValues)
      .setModuleRoots(modulePaths, project.basePath!!, libraryName.get(), packageName.get())
      .setJavaVersion(project)
    templateValues[TemplateMetadata.ATTR_CLASS_NAME] = className.get()
    templateValues[TemplateMetadata.ATTR_IS_NEW_PROJECT] = true
    templateValues[TemplateMetadata.ATTR_IS_LIBRARY_MODULE] = true
    val moduleRoot = modulePaths.moduleRoot!!
    if (doDryRun(moduleRoot, templateValues)) {
      render(moduleRoot, templateValues)
    }
  }

  private fun doDryRun(moduleRoot: File, templateValues: Map<String, Any>): Boolean =
    renderTemplate(true, project, moduleRoot, templateValues, null)

  private fun render(moduleRoot: File, templateValues: Map<String, Any>) {
    val filesToOpen = mutableListOf<File>()
    val success = renderTemplate(false, project, moduleRoot, templateValues, filesToOpen)
    if (success) {
      // calling smartInvokeLater will make sure that files are open only when the project is ready
      DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, true) }
      myProjectSyncInvoker.syncProject(project)
    }
  }

  private fun renderTemplate(
    dryRun: Boolean, project: Project, moduleRoot: File, templateValues: Map<String, Any>, filesToOpen: MutableList<File>?
  ): Boolean {
    val template = myTemplateHandle.template

      val context = Builder.newContext(template,  project)
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