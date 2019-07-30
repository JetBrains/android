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
import com.android.tools.idea.templates.TemplateMetadata
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.collect.Maps
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Modal
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import java.util.ArrayList

class NewBenchmarkModuleModel(
  val project: Project,
  private val myTemplateHandle: TemplateHandle,
  private val myProjectSyncInvoker: ProjectSyncInvoker) : WizardModel() {
  private val myModuleName: StringProperty = StringValueProperty("benchmark")
  private val myPackageName: StringProperty = StringValueProperty()
  private val myLanguage: OptionalProperty<Language> = OptionalValueProperty(getInitialSourceLanguage(project))
  private val myMinSdk: OptionalProperty<VersionItem> = OptionalValueProperty()

  fun moduleName(): StringProperty {
    return myModuleName
  }

  fun packageName(): StringProperty {
    return myPackageName
  }

  fun language(): OptionalProperty<Language> {
    return myLanguage
  }

  fun minSdk(): OptionalProperty<VersionItem> {
    return myMinSdk
  }

  override fun handleFinished() {
    object : Modal(project, message(
      "android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        val modulePaths = createDefaultTemplateAt(myProject.basePath!!, moduleName().get()).paths
        val myTemplateValues: MutableMap<String, Any>? = Maps.newHashMap()
        TemplateValueInjector(myTemplateValues!!)
          .setProjectDefaults(myProject)
          .setModuleRoots(modulePaths, myProject.basePath!!, moduleName().get(), packageName().get())
          .setJavaVersion(myProject)
          .setLanguage(myLanguage.value)
          .setBuildVersion(myMinSdk.value, myProject)
        myTemplateValues[TemplateMetadata.ATTR_APP_TITLE] = moduleName().get()
        myTemplateValues[TemplateMetadata.ATTR_IS_NEW_PROJECT] = false
        myTemplateValues[TemplateMetadata.ATTR_IS_LIBRARY_MODULE] = true
        val moduleRoot = modulePaths.moduleRoot!!
        if (doDryRun(moduleRoot, myTemplateValues)) {
          render(moduleRoot, myTemplateValues)
        }
      }
    }.queue()
  }

  private fun doDryRun(moduleRoot: File, templateValues: Map<String, Any>): Boolean {
    return renderTemplate(true, project, moduleRoot, templateValues, null)
  }

  private fun render(moduleRoot: File, templateValues: Map<String, Any>) {
    val filesToOpen: List<File> = ArrayList()
    val success = renderTemplate(false, project, moduleRoot, templateValues, filesToOpen)
    if (success) {
      // calling smartInvokeLater will make sure that files are open only when the project is ready
      DumbService.getInstance(project).smartInvokeLater { openEditors(project, filesToOpen, true) }
      myProjectSyncInvoker.syncProject(project)
    }
  }

  private fun renderTemplate(
    dryRun: Boolean, project: Project, moduleRoot: File, templateValues: Map<String, Any>, filesToOpen: List<File>?
  ): Boolean {
    val template = myTemplateHandle.template

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