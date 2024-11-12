/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.npw.baselineprofiles

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.generateBaselineProfilesModule
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class NewBaselineProfilesModuleModel(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker,
) : ModuleModel(
  name = "baselineProfile",
  commandName = "New Baseline Profile Module",
  isLibrary = true,
  projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
  moduleParent = moduleParent,
  wizardContext = WizardUiContext.NEW_MODULE,
) {
  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = AndroidStudioEvent.TemplateRenderer.BASELINE_PROFILES_MODULE

  val targetModule = OptionalValueProperty<Module>()
  val useGmd = BoolValueProperty(false)

  override fun getParamsToLog(): String {
    return super.getParamsToLog() + """
      |
      |[Baseline Profile Generator params]
      |Target application: ${targetModule.valueOrNull ?: "N/A"}
      |Use GMD: ${useGmd.get()}
    """.trimMargin()
  }

  override val renderer: MultiTemplateRenderer.TemplateRenderer
    get() = object : ModuleTemplateRenderer() {
      override val recipe: Recipe
        get() = {
          generateBaselineProfilesModule(
            newModule = it as ModuleTemplateData,
            useGradleKts = useGradleKts.get(),
            useGmd = useGmd.get(),
            targetModule = targetModule.value,
            useVersionCatalog = useVersionCatalog.get()
          )
        }
    }
}

/**
 * Checks [targetModule] for minSdk and applies maxOf(targetModule.minSdk, SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION)
 */
fun getBaselineProfilesMinSdk(targetModule: Module?): Int {
  val targetModuleMinSdk = getModuleMinSdk(targetModule)

  var minSdk = SdkVersionInfo.LOWEST_PROFILE_GUIDED_OPTIMIZATIONS_SDK_VERSION
  if (targetModuleMinSdk != null && targetModuleMinSdk > minSdk) {
    minSdk = targetModuleMinSdk
  }

  return minSdk
}

@VisibleForTesting
fun getModuleMinSdk(targetModule: Module?): Int? {
  targetModule ?: return null
  val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project) ?: return null
  val targetModuleAndroidModel = projectBuildModel.getModuleBuildModel(targetModule)?.android() ?: return null

  return targetModuleAndroidModel.defaultConfig().minSdkVersion().toInt()
}
