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

import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.FormFactor.Companion.get
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.getTemplateIcon
import com.android.tools.idea.templates.Template.ANDROID_PROJECT_TEMPLATE
import com.android.tools.idea.templates.Template.CATEGORY_APPLICATION
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File
import javax.swing.Icon

class NewAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleTemplateGalleryEntry> {
    val manager = TemplateManager.getInstance()!!

    return manager.getTemplatesInCategory(CATEGORY_APPLICATION)
      .filter { manager.getTemplateMetadata(it)?.formFactor != null }
      .flatMap { templateFile ->
        val metadata = manager.getTemplateMetadata(templateFile)!!
        val minSdk = metadata.minSdk
        val formFactor = get(metadata.formFactor!!)

        fun File.getIcon(): Icon = getTemplateIcon(TemplateHandle(this))!!
        fun createTemplateEntry(isLibrary: Boolean, icon: Icon, title: String = metadata.title!!) =
          if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get() && title == message("android.wizard.module.new.mobile"))
            AndroidModuleTemplateGalleryEntry(
              null,
              FormFactor.MOBILE,
              LOWEST_ACTIVE_API,
              false,
              AndroidIcons.Wizards.AndroidModule,
              title,
              message("android.wizard.module.new.mobile.description")
            )
          else
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
    override val templateFile: File?,
    override val formFactor: FormFactor,
    private val minSdkLevel: Int,
    override val isLibrary: Boolean,
    override val icon: Icon,
    override val name: String,
    override val description: String
  ) : ModuleTemplateGalleryEntry {
    override fun toString(): String = name

    override fun createStep(project: Project, projectSyncInvoker: ProjectSyncInvoker, moduleParent: String?): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidModuleModel(project, moduleParent, projectSyncInvoker, createDummyTemplate(), isLibrary, templateFile)
      return ConfigureAndroidModuleStep(model, formFactor, minSdkLevel, basePackage, name)
    }
  }
}

