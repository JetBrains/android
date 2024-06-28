/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.multiplatform

import com.android.annotations.concurrency.WorkerThread
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.generateMultiplatformModule
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.projectsystem.KotlinMultiplatformModulePathsImpl
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent
import com.intellij.openapi.project.Project

class NewKotlinMultiplatformLibraryModuleModel(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker,
  name: String = "kmplibrary",
) : ModuleModel(
  name = name,
  commandName = "New Kotlin Multiplatform Library Module",
  isLibrary = true,
  _template = GradleAndroidModuleTemplate.createMultiplatformModuleTemplate(project, name),
  projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
  moduleParent = moduleParent,
  wizardContext = NEW_MODULE,
) {

  override val androidSdkInfo = OptionalValueProperty(
    AndroidVersionsInfo().apply { loadLocalVersions() }
      .getKnownTargetVersions(FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API)
      .first()
  )

  override val renderer: MultiTemplateRenderer.TemplateRenderer = object : ModuleTemplateRenderer() {

    @WorkerThread
    override fun init() {
      super.init()

      moduleTemplateDataBuilder.apply {
        commonSrcDir = (template.get().paths as KotlinMultiplatformModulePathsImpl)
          .getCommonSrcDirectory(this@NewKotlinMultiplatformLibraryModuleModel.packageName.get())
      }
    }
    override val recipe: Recipe
      get() = { td: TemplateData ->
        generateMultiplatformModule(
          data = td as ModuleTemplateData,
          useKts = true
        )
      }
  }

  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = RenderLoggingEvent.KOTLIN_MULTIPLATFORM_LIBRARY_MODULE

}