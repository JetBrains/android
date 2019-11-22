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
package com.android.tools.idea.npw.dynamicapp

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
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
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_FUSING
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_ON_DEMAND
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_FEATURE_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_DYNAMIC_IS_INSTANT_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_SIMPLE_NAME
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.template.BaseFeature
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion

class DynamicFeatureModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker, isInstant: Boolean
) : ModuleModel(project, templateHandle, projectSyncInvoker, "dynamicfeature") {
  @JvmField val featureTitle = StringValueProperty("Module Title")
  @JvmField val packageName = StringValueProperty()
  @JvmField val language = OptionalValueProperty(getInitialSourceLanguage(project))
  @JvmField val androidSdkInfo = OptionalValueProperty<VersionItem>()
  @JvmField val baseApplication = OptionalValueProperty<Module>()
  @JvmField val featureOnDemand = BoolValueProperty(true)
  @JvmField val featureFusing = BoolValueProperty(true)
  @JvmField val instantModule = BoolValueProperty(false)
  @JvmField val deviceFeatures = ObservableList<DeviceFeatureModel>()
  @JvmField val downloadInstallKind =
    OptionalValueProperty(if (isInstant) DownloadInstallKind.INCLUDE_AT_INSTALL_TIME else DownloadInstallKind.ON_DEMAND_ONLY)

  override val renderer = object: ModuleTemplateRenderer() {
    override fun init() {
      super.init()
      val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      val newValues = mutableMapOf(
          ATTR_IS_DYNAMIC_FEATURE to true,
          ATTR_MODULE_SIMPLE_NAME to nameToJavaPackage(moduleName.get()),
          ATTR_DYNAMIC_FEATURE_TITLE to featureTitle.get(),
          ATTR_DYNAMIC_FEATURE_ON_DEMAND to featureOnDemand.get(),
          ATTR_DYNAMIC_FEATURE_FUSING to featureFusing.get(),
          ATTR_IS_NEW_MODULE to true,
          ATTR_IS_LIBRARY_MODULE to false,
          ATTR_DYNAMIC_IS_INSTANT_MODULE to instantModule.get(),
          // Dynamic delivery conditions
          ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME),
          ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS),
          ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY to (downloadInstallKind.value == DownloadInstallKind.ON_DEMAND_ONLY),
          ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST to deviceFeatures
      )

      TemplateValueInjector(newValues)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setLanguage(language.value)
        .setBuildVersion(androidSdkInfo.value, project, false)
        .setBaseFeature(baseApplication.value)

      templateValues.putAll(newValues)

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            setProjectDefaults(project)
            language = this@DynamicFeatureModel.language.value
            javaVersion = JavaVersion.parse("1.8")
          }
          isLibrary = false
          setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), this@DynamicFeatureModel.packageName.get())
          setBuildVersion(androidSdkInfo.value, project)
        }
      }
    }

    // TODO(qumeric): move it to ModuleModel when all modules will support the new system
    override fun renderTemplate(dryRun: Boolean, project: Project, runFromTemplateRenderer: Boolean): Boolean {
      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        val context = RenderingContext2(
          project = project,
          module = null,
          commandName = "New Dynamic Feature Module",
          templateData = moduleTemplateDataBuilder.build(),
          moduleRoot = getModuleRoot(project.basePath!!, moduleName.get()),
          dryRun = dryRun,
          showErrors = true
        )
        val executor = if (dryRun) FindReferencesRecipeExecutor2(context) else DefaultRecipeExecutor2(context)
        val recipe: Recipe = { td: TemplateData -> /* TODO generateDynamicFeatureModel(td as ModuleTemplateData) */ }
        return recipe.doRender(context, executor)
      }
      return super.renderTemplate(dryRun, project, runFromTemplateRenderer)
    }
  }
}