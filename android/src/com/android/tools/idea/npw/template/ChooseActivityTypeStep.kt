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
import com.android.tools.idea.npw.project.AndroidPackageUtils
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.TemplateManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Step for the gallery for Activity templates.
 */
class ChooseActivityTypeStep(moduleModel: NewModuleModel,
                             renderModel: RenderTemplateModel,
                             formFactor: FormFactor,
                             moduleTemplates: List<NamedModuleTemplate>)
  : ChooseGalleryItemStep(moduleModel, renderModel, formFactor,
                          moduleTemplates,
                          messageKeys = ActivityGalleryStepMessageKeys(),
                          emptyItemLabel = "Empty Activity") {
 
  constructor(moduleModel: NewModuleModel, renderModel: RenderTemplateModel, formFactor: FormFactor, targetDirectory: VirtualFile)
    : this(moduleModel, renderModel, formFactor, AndroidPackageUtils.getModuleTemplates(renderModel.androidFacet!!, targetDirectory))

  override val templateRenders = (if (isNewModule) listOf(TemplateRenderer(null)) else listOf()) +
                                 TemplateManager.getInstance().getTemplateList(formFactor).map(::TemplateRenderer)
}

class ActivityGalleryStepMessageKeys : WizardGalleryItemsStepMessageKeys {

  override val addMessage: String = "android.wizard.activity.add"
  override val stepTitle: String = "android.wizard.config.activity.title"
  override val itemNotFound: String = "android.wizard.activity.not.found"
  override val invalidMinSdk: String = "android.wizard.activity.invalid.min.sdk"
  override val invalidMinBuild: String = "android.wizard.activity.invalid.min.build"
  override val invalidAndroidX: String = "android.wizard.activity.invalid.androidx"
}
