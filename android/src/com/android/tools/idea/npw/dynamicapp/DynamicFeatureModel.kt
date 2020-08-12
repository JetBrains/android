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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.generateDynamicFeatureModule
import com.android.tools.idea.observable.collections.ObservableList
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent

class DynamicFeatureModel(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker,
  val isInstant: Boolean,
  val templateName: String,
  val templateDescription: String
) : ModuleModel(
  name = "dynamicfeature",
  commandName = "New Dynamic Feature Module",
  isLibrary = false,
  projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
  moduleParent = moduleParent,
  wizardContext = NEW_MODULE
) {
  val featureTitle = StringValueProperty("Module Title")
  val baseApplication = OptionalValueProperty<Module>()
  // TODO(qumeric): investigate why featureOnDemand = !isInstant disappeared
  val featureFusing = BoolValueProperty(true)
  val deviceFeatures = ObservableList<DeviceFeatureModel>()
  val downloadInstallKind =
    OptionalValueProperty(if (isInstant) DownloadInstallKind.INCLUDE_AT_INSTALL_TIME else DownloadInstallKind.ON_DEMAND_ONLY)

  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = if (isInstant) RenderLoggingEvent.INSTANT_DYNAMIC_FEATURE_MODULE else RenderLoggingEvent.DYNAMIC_FEATURE_MODULE

  override val renderer = object : ModuleTemplateRenderer() {
    override val recipe: Recipe get() = { td: TemplateData ->
      generateDynamicFeatureModule(
        td as ModuleTemplateData,
        isInstant,
        featureTitle.get(),
        featureFusing.get(),
        downloadInstallKind.value,
        deviceFeatures,
        useGradleKts.get()
      )
    }

    @WorkerThread
    override fun init() {
      super.init()

      moduleTemplateDataBuilder.apply {
        projectTemplateDataBuilder.apply {
          javaVersion = JavaVersion.parse("1.8")
        }
        setBaseFeature(baseApplication.value)
      }
    }
  }
}