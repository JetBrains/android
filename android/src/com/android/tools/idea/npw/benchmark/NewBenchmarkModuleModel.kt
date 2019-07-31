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
package com.android.tools.idea.npw.benchmark

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_PROJECT
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Modal
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File

class NewBenchmarkModuleModel(
  val project: Project,
  private val templateHandle: TemplateHandle,
  private val projectSyncInvoker: ProjectSyncInvoker) : WizardModel() {
  @JvmField val moduleName: StringProperty = StringValueProperty("benchmark")
  @JvmField val packageName: StringProperty = StringValueProperty()
  @JvmField val language: OptionalProperty<Language> = OptionalValueProperty(getInitialSourceLanguage(project))
  @JvmField val minSdk: OptionalProperty<VersionItem> = OptionalValueProperty()

  override fun handleFinished() {
    object : Modal(project, message(
      "android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        val modulePaths = createDefaultTemplateAt(myProject.basePath!!, moduleName.get()).paths
        val templateValues: MutableMap<String, Any> = mutableMapOf()
        TemplateValueInjector(templateValues)
          .setProjectDefaults(myProject)
          .setModuleRoots(modulePaths, myProject.basePath!!, moduleName.get(), packageName.get())
          .setJavaVersion(myProject)
          .setLanguage(language.value)
          .setBuildVersion(minSdk.value, myProject)
        templateValues[ATTR_APP_TITLE] = moduleName.get()
        templateValues[ATTR_IS_NEW_PROJECT] = false
        templateValues[ATTR_IS_LIBRARY_MODULE] = true
        val moduleRoot = modulePaths.moduleRoot!!
        if (doDryRun(moduleRoot, templateValues)) {
          render(moduleRoot, templateValues)
        }
      }
    }.queue()
  }

  private fun doDryRun(moduleRoot: File, templateValues: Map<String, Any>): Boolean =
    renderTemplate(true, project, moduleRoot, templateValues, null)

  private fun render(moduleRoot: File, templateValues: Map<String, Any>) {
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