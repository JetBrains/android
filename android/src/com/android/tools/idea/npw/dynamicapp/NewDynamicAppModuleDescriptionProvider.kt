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

import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import javax.swing.Icon

const val DYNAMIC_FEATURE_TEMPLATE = "Dynamic Feature"
const val INSTANT_DYNAMIC_FEATURE_TEMPLATE = "Dynamic Feature (Instant App)"

class NewDynamicAppModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project?): Collection<ModuleGalleryEntry> = listOf(
    FeatureTemplateGalleryEntry(false),
    FeatureTemplateGalleryEntry(true))

  private class FeatureTemplateGalleryEntry(private val isInstant: Boolean) : ModuleTemplateGalleryEntry {
    private val templateFile: File = TemplateManager.getInstance().getTemplateFile(
      CATEGORY_APPLICATION, if (isInstant) INSTANT_DYNAMIC_FEATURE_TEMPLATE else DYNAMIC_FEATURE_TEMPLATE)!!
    private val templateHandle: TemplateHandle = TemplateHandle(templateFile)

    override fun getIcon(): Icon? = getTemplateIcon(templateHandle, false)
    override fun getName(): String =
      message(if (isInstant) "android.wizard.module.new.dynamic.module.instant" else "android.wizard.module.new.dynamic.module")

    override fun getDescription(): String? = templateHandle.metadata.description
    override fun toString(): String = name
    override fun getTemplateFile(): File = templateFile
    override fun getFormFactor() = FormFactor.MOBILE
    override fun isLibrary() = false
    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      return ConfigureDynamicModuleStep(
        DynamicFeatureModel(model.project.value, templateHandle, model.projectSyncInvoker, isInstant), basePackage, isInstant)
    }
  }
}
