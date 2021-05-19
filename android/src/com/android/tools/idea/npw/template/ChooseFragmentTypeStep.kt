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

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.wizard.template.WizardUiContext
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile

/**
 * Step for the gallery for Fragment templates.
 */
class ChooseFragmentTypeStep(
  renderModel: RenderTemplateModel,
  formFactor: FormFactor,
  targetDirectory: VirtualFile
) : ChooseGalleryItemStep(
  renderModel, formFactor, targetDirectory,
  messageKeys = fragmentGalleryStepMessageKeys,
  emptyItemLabel = "Blank Fragment"
) {
  override val templateRenderers: List<TemplateRenderer> = TemplateResolver.getAllTemplates()
    .filter { WizardUiContext.FragmentGallery in it.uiContexts }
    .map(::NewTemplateRenderer)
}

@VisibleForTesting
val fragmentGalleryStepMessageKeys = WizardGalleryItemsStepMessageKeys(
  "android.wizard.fragment.add",
  "android.wizard.config.fragment.title",
  "android.wizard.fragment.not.found",
  "android.wizard.fragment.invalid.min.sdk",
  "android.wizard.fragment.invalid.min.build",
  "android.wizard.fragment.invalid.androidx",
  "android.wizard.fragment.invalid.jetifier",
  "android.wizard.fragment.invalid.needs.kotlin"
)
