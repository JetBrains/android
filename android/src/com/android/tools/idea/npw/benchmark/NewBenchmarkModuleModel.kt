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

import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.model.doRender
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.getModuleRoot
import com.android.tools.idea.npw.module.recipes.benchmarkModule.generateBenchmarkModule
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion

class NewBenchmarkModuleModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(project, templateHandle, projectSyncInvoker, "benchmark") {
  @JvmField val packageName = StringValueProperty()
  @JvmField val language = OptionalValueProperty(getInitialSourceLanguage(project))
  @JvmField val minSdk = OptionalValueProperty<VersionItem>()

  override val renderer = object : ModuleTemplateRenderer() {
    override fun init() {
      super.init()
      val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      val newValues = mutableMapOf(
          ATTR_APP_TITLE to moduleName.get(),
          ATTR_IS_NEW_MODULE to true,
          ATTR_IS_LIBRARY_MODULE to true
      )

      TemplateValueInjector(newValues)
        .setBuildVersion(minSdk.value, project, false)
        .setProjectDefaults(project, false)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setJavaVersion(project)
        .setLanguage(language.value)

      templateValues.putAll(newValues)

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            setProjectDefaults(project)
            language = this@NewBenchmarkModuleModel.language.value
            javaVersion = JavaVersion.parse("1.8")
          }
          // TODO(qumeric): will it fail if there are no SDKs installed?
          val anyTargetVersion = AndroidVersionsInfo().apply { loadLocalVersions() }
            .getKnownTargetVersions(FormFactor.MOBILE, LOWEST_ACTIVE_API)
            .first() // we don't care which one do we use, we just have to pass something, it is not going to be used
          setBuildVersion(anyTargetVersion, project)
          isLibrary = true
          setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), this@NewBenchmarkModuleModel.packageName.get())
        }
      }
    }

    // TODO(qumeric): move it to ModuleModel when all modules will support the new system
    override fun renderTemplate(dryRun: Boolean, project: Project, runFromTemplateRenderer: Boolean): Boolean {
      val moduleRoot = getModuleRoot(project.basePath!!, moduleName.get())
      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        val context = RenderingContext2(
          project = project,
          module = null,
          commandName = "New Benchmark Module",
          templateData = moduleTemplateDataBuilder.build(),
          moduleRoot = moduleRoot,
          dryRun = dryRun,
          showErrors = true
        )
        val executor = if (dryRun) FindReferencesRecipeExecutor2(context) else DefaultRecipeExecutor2(context)
        val recipe: Recipe = { td: TemplateData -> generateBenchmarkModule(td as ModuleTemplateData) }
        return recipe.doRender(context, executor)
      }
      return super.renderTemplate(dryRun, project, runFromTemplateRenderer)
    }
  }
}