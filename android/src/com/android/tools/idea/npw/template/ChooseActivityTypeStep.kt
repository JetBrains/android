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


import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.TemplateManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile

/**
 * Step for the gallery for Activity templates.
 */
class ChooseActivityTypeStep(
  moduleModel: NewModuleModel,
  renderModel: RenderTemplateModel,
  formFactor: FormFactor,
  moduleTemplates: List<NamedModuleTemplate>
) : ChooseGalleryItemStep(
  moduleModel, renderModel, formFactor, moduleTemplates,
  messageKeys = activityGalleryStepMessageKeys,
  emptyItemLabel = "Empty Activity"
) {
  constructor(moduleModel: NewModuleModel, renderModel: RenderTemplateModel, formFactor: FormFactor, targetDirectory: VirtualFile)
    : this(moduleModel, renderModel, formFactor, renderModel.androidFacet!!.getModuleTemplates(targetDirectory))

  override val templateRenders = (if (isNewModule) listOf(TemplateRenderer(null)) else listOf()) +
                                 TemplateManager.getInstance().getTemplateList(formFactor).map(::TemplateRenderer)
}

@VisibleForTesting
val activityGalleryStepMessageKeys = WizardGalleryItemsStepMessageKeys(
  "android.wizard.activity.add",
  "android.wizard.config.activity.title",
  "android.wizard.activity.not.found",
  "android.wizard.activity.invalid.min.sdk",
  "android.wizard.activity.invalid.min.build",
  "android.wizard.activity.invalid.androidx",
  "android.wizard.activity.invalid.needs.kotlin"
)
