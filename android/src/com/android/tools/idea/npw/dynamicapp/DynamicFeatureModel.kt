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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.NewProjectModel.Companion.nameToJavaPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_FUSING
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_SUPPORTS_DYNAMIC_DELIVERY
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_FEATURE_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DYNAMIC_IS_INSTANT_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_DYNAMIC_FEATURE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_PROJECT
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_SIMPLE_NAME
import com.android.tools.idea.templates.recipe.RenderingContext.Builder
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.collect.Maps
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Modal
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File

class DynamicFeatureModel(val project: Project,
                          val templateHandle: TemplateHandle,
                          private val myProjectSyncInvoker: ProjectSyncInvoker,
                          isInstant: Boolean) : WizardModel() {
  private val myModuleName: StringProperty = StringValueProperty("dynamicfeature")
  private val myFeatureTitle: StringProperty = StringValueProperty("Module Title")
  private val myPackageName: StringProperty = StringValueProperty()
  private val myAndroidSdkInfo: OptionalProperty<VersionItem> = OptionalValueProperty()
  private val myBaseApplication: OptionalProperty<Module> = OptionalValueProperty()
  private val myFeatureOnDemand: BoolProperty = BoolValueProperty(true)
  private val myFeatureFusing: BoolProperty = BoolValueProperty(true)
  private val myInstantModule: BoolProperty = BoolValueProperty(false)
  private val myDeviceFeatures = ObservableList<DeviceFeatureModel>()
  private val myDownloadInstallKind: OptionalProperty<DownloadInstallKind> =
    if (isInstant)
      OptionalValueProperty(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME)
    else
      OptionalValueProperty( DownloadInstallKind.ON_DEMAND_ONLY)

  fun moduleName(): StringProperty {
    return myModuleName
  }

  fun featureTitle(): StringProperty {
    return myFeatureTitle
  }

  fun packageName(): StringProperty {
    return myPackageName
  }

  fun baseApplication(): OptionalProperty<Module> {
    return myBaseApplication
  }

  fun androidSdkInfo(): OptionalProperty<VersionItem> {
    return myAndroidSdkInfo
  }

  fun featureOnDemand(): BoolProperty {
    return myFeatureOnDemand
  }

  fun downloadInstallKind(): OptionalProperty<DownloadInstallKind> {
    return myDownloadInstallKind
  }

  fun deviceFeatures(): ObservableList<DeviceFeatureModel> {
    return myDeviceFeatures
  }

  fun featureFusing(): BoolProperty {
    return myFeatureFusing
  }

  fun instantModule(): BoolProperty {
    return myInstantModule
  }

  override fun handleFinished() {
    object : Modal(project, message(
      "android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        val modulePaths = createDefaultTemplateAt(
          myProject.basePath!!, moduleName().get()).paths
        val myTemplateValues: MutableMap<String, Any>? = Maps.newHashMap()
        TemplateValueInjector(myTemplateValues!!)
          .setModuleRoots(modulePaths, myProject.basePath!!, moduleName().get(), packageName().get())
          .setBuildVersion(androidSdkInfo().value, myProject).setBaseFeature(baseApplication().value)

        myTemplateValues.let {
          it[ATTR_IS_DYNAMIC_FEATURE] = true
          it[ATTR_MODULE_SIMPLE_NAME] = nameToJavaPackage(moduleName().get())
          it[ATTR_DYNAMIC_FEATURE_TITLE] = featureTitle().get()
          it[ATTR_DYNAMIC_FEATURE_ON_DEMAND] = featureOnDemand().get()
          it[ATTR_DYNAMIC_FEATURE_FUSING] = featureFusing().get()
          it[ATTR_IS_NEW_PROJECT] = true
          it[ATTR_IS_LIBRARY_MODULE] = false
          it[ATTR_DYNAMIC_IS_INSTANT_MODULE] = instantModule().get()
          // Dynamic delivery conditions
          it[ATTR_DYNAMIC_FEATURE_SUPPORTS_DYNAMIC_DELIVERY] = StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.get()
          it[ATTR_DYNAMIC_FEATURE_INSTALL_TIME_DELIVERY] = myDownloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME
          it[ATTR_DYNAMIC_FEATURE_INSTALL_TIME_WITH_CONDITIONS_DELIVERY] = myDownloadInstallKind.value == DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS
          it[ATTR_DYNAMIC_FEATURE_ON_DEMAND_DELIVERY] = myDownloadInstallKind.value == DownloadInstallKind.ON_DEMAND_ONLY
          it[ATTR_DYNAMIC_FEATURE_DEVICE_FEATURE_LIST] = myDeviceFeatures
        }
        val moduleRoot = modulePaths.moduleRoot!!
        if (doDryRun(moduleRoot, myTemplateValues)) {
          render(moduleRoot, myTemplateValues)
        }
      }
    }.queue()
  }

  private fun doDryRun(moduleRoot: File, templateValues: Map<String, Any>): Boolean {
    return renderTemplate(true, project, moduleRoot, templateValues)
  }

  private fun render(moduleRoot: File, templateValues: Map<String, Any>) {
    renderTemplate(false, project, moduleRoot, templateValues)
    myProjectSyncInvoker.syncProject(project)
  }

  private fun renderTemplate(dryRun: Boolean,
                             project: Project,
                             moduleRoot: File,
                             templateValues: Map<String, Any>): Boolean {
    val template = templateHandle.template

    val context = Builder.newContext(template, project)
      .withCommandName(message("android.wizard.module.new.module.menu.description"))
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(moduleRoot)
      .withParams(templateValues)
      .build()

    return template.render(context!!, dryRun)
  }
}