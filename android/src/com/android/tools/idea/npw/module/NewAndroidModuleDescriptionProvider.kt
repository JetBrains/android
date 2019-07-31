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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.FormFactor.Companion.get
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.templates.Template.ANDROID_PROJECT_TEMPLATE
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import javax.swing.Icon

class NewAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project?): Collection<ModuleTemplateGalleryEntry>? {
    val manager = TemplateManager.getInstance()!!
    return manager.getTemplatesInCategory(CATEGORY_APPLICATION)
      .filter { manager.getTemplateMetadata(it)?.formFactor != null }
      .flatMap { templateFile ->
        val metadata = manager.getTemplateMetadata(templateFile)!!
        val minSdk = metadata.minSdk
        val formFactor = get(metadata.formFactor!!)

        fun File.getIcon(): Icon = getTemplateIcon(TemplateHandle(this), false)!!
        fun createTemplateEntry(isLibrary: Boolean, icon: Icon, title: String = metadata.title!!) =
          AndroidModuleTemplateGalleryEntry(templateFile, formFactor, minSdk, isLibrary, icon, title, metadata.description!!)

        val templateIcon = templateFile.getIcon()

        if (formFactor == FormFactor.MOBILE) {
          val androidProjectTemplate = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_PROJECT_TEMPLATE)!!
          listOf(
            createTemplateEntry(false, templateIcon, message("android.wizard.module.new.mobile")),
            createTemplateEntry(true, androidProjectTemplate.getIcon(), message("android.wizard.module.new.library"))
          )
        }
        else {
          listOf(createTemplateEntry(false, templateIcon))
        }
      }
  }

  private class AndroidModuleTemplateGalleryEntry(
    override val templateFile: File,
    override val formFactor: FormFactor,
    private val minSdkLevel: Int,
    override val isLibrary: Boolean,
    override val icon: Icon,
    override val name: String,
    override val description: String) : ModuleTemplateGalleryEntry {
    override fun toString(): String = name

    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      return ConfigureAndroidModuleStep(model, formFactor, minSdkLevel, basePackage, name)
    }
  }
}

