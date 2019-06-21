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
package com.android.tools.idea.npw.template


import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.templates.TemplateManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Step for the gallery for Fragment templates.
 */
class ChooseFragmentTypeStep(moduleModel: NewModuleModel,
                             renderModel: RenderTemplateModel,
                             formFactor: FormFactor,
                             targetDirectory: VirtualFile)
  : ChooseGalleryItemStep(moduleModel, renderModel, formFactor,
                          targetDirectory,
                          messageKeys = FragmentGalleryStepMessageKeys(),
                          emptyItemLabel = "Blank Fragment") {

  override val templateRenders = (if (isNewModule) listOf(TemplateRenderer(null)) else listOf()) +
                                 TemplateManager.getInstance().getFragmentTemplateList(formFactor).map(::TemplateRenderer)
}

class FragmentGalleryStepMessageKeys : WizardGalleryItemsStepMessageKeys {

  override val addMessage: String = "android.wizard.fragment.add"
  override val stepTitle: String = "android.wizard.config.fragment.title"
  override val itemNotFound: String = "android.wizard.fragment.not.found"
  override val invalidMinSdk: String = "android.wizard.fragment.invalid.min.sdk"
  override val invalidMinBuild: String = "android.wizard.fragment.invalid.min.build"
  override val invalidAndroidX: String = "android.wizard.fragment.invalid.androidx"
}
