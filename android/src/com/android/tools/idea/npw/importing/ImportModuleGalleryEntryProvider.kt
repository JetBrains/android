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
package com.android.tools.idea.npw.importing

import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.templates.Template.ANDROID_PROJECT_TEMPLATE
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class ImportModuleGalleryEntryProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project?): Collection<ModuleGalleryEntry?> = listOf(
    SourceImportModuleGalleryEntry(message("android.wizard.module.import.eclipse.title")),
    SourceImportModuleGalleryEntry(message("android.wizard.module.import.gradle.title")),
    ArchiveImportModuleGalleryEntry()
  )

  private class SourceImportModuleGalleryEntry(templateName: String) : ModuleGalleryEntry {
    private val templateHandle = TemplateHandle(TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, templateName)!!)

    override val icon: Icon? = getTemplateIcon(templateHandle, false)
    override val name: String = templateHandle.metadata.title!!
    override val description: String? = templateHandle.metadata.description
    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> =
      SourceToGradleModuleStep(SourceToGradleModuleModel(model.project.value, model.projectSyncInvoker))
  }

  private class ArchiveImportModuleGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon?
      get() {
        val androidModuleTemplate = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_PROJECT_TEMPLATE)
        return getTemplateIcon(TemplateHandle(androidModuleTemplate!!), false)
      }

    override val name: String = message("android.wizard.module.import.title")
    override val description: String = message("android.wizard.module.import.description")
    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> =
      ArchiveToGradleModuleStep(ArchiveToGradleModuleModel(model.project.value, model.projectSyncInvoker))
  }
}