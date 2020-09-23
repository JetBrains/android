/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.toTemplateFormFactor
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile

/**
 * Step for the gallery for Activity templates.
 */
class ChooseActivityTypeStep private constructor(
  renderModel: RenderTemplateModel,
  formFactor: FormFactor,
  moduleTemplates: List<NamedModuleTemplate>,
  androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty.absent(),
  private val wizardUiContext: WizardUiContext
) : ChooseGalleryItemStep(
  renderModel, formFactor, moduleTemplates, activityGalleryStepMessageKeys, "Empty Activity", androidSdkInfo
) {
  override val templateRenderers: List<TemplateRenderer> = sequence {
    if (isNewModule) {
      yield(NewTemplateRenderer(Template.NoActivity))
    }
    yieldAll(TemplateResolver.getAllTemplates()
               .filter { wizardUiContext in it.uiContexts }
               .filter { formFactor.toTemplateFormFactor() == it.formFactor }
               .map(::NewTemplateRenderer))
  }.toList()

  companion object Factory {
    fun forNewModule(
      renderModel: RenderTemplateModel,
      formFactor: FormFactor,
      androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty.absent()
    ) = ChooseActivityTypeStep(renderModel, formFactor, listOf(), androidSdkInfo, WizardUiContext.NewModule)

    fun forActivityGallery(
      renderModel: RenderTemplateModel,
      targetDirectory: VirtualFile
    ) = ChooseActivityTypeStep(
      renderModel = renderModel,
      formFactor = FormFactor.MOBILE,
      moduleTemplates = renderModel.androidFacet!!.getModuleTemplates(targetDirectory),
      wizardUiContext = WizardUiContext.ActivityGallery
    )
  }
}

@VisibleForTesting
val activityGalleryStepMessageKeys = WizardGalleryItemsStepMessageKeys(
  "android.wizard.activity.add",
  "android.wizard.config.activity.title",
  "android.wizard.activity.not.found",
  "android.wizard.activity.invalid.min.sdk",
  "android.wizard.activity.invalid.min.build",
  "android.wizard.activity.invalid.androidx",
  "android.wizard.activity.invalid.jetifier",
  "android.wizard.activity.invalid.needs.kotlin"
)
