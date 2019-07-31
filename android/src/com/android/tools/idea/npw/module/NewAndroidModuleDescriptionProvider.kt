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
import java.util.ArrayList
import javax.swing.Icon

class NewAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project?): Collection<ModuleTemplateGalleryEntry?>? {
    val res = ArrayList<ModuleTemplateGalleryEntry?>()
    val manager = TemplateManager.getInstance()
    val applicationTemplates: List<File?> = manager!!.getTemplatesInCategory(CATEGORY_APPLICATION)
    for (templateFile in applicationTemplates) {
      val metadata = manager.getTemplateMetadata(templateFile!!)
      if (metadata == null || metadata.formFactor == null) {
        continue
      }
      val minSdk = metadata.minSdk
      val formFactor = get(metadata.formFactor!!)
      if (formFactor == FormFactor.MOBILE) {
        res.add(AndroidModuleTemplateGalleryEntry(
          templateFile, formFactor, minSdk, false, getModuleTypeIcon(templateFile)!!,
          message("android.wizard.module.new.mobile"), metadata.title!!))
        val androidProjectTemplate = TemplateManager.getInstance().getTemplateFile(CATEGORY_APPLICATION, ANDROID_PROJECT_TEMPLATE)!!
        res.add(AndroidModuleTemplateGalleryEntry(
          templateFile, formFactor, minSdk, true, getModuleTypeIcon(androidProjectTemplate)!!,
          message("android.wizard.module.new.library"), metadata.description!!))
      }
      else {
        res.add(AndroidModuleTemplateGalleryEntry(
          templateFile, formFactor, minSdk, false, getModuleTypeIcon(templateFile)!!, metadata.title!!, metadata.description!!))
      }
    }
    return res
  }

  private class AndroidModuleTemplateGalleryEntry internal constructor(
    private val myTemplateFile: File,
    private val myFormFactor: FormFactor,
    private val myMinSdkLevel: Int,
    private val myIsLibrary: Boolean,
    private val myIcon: Icon,
    private val myName: String,
    private val myDescription: String) : ModuleTemplateGalleryEntry {
    override fun getTemplateFile(): File = myTemplateFile
    override fun getFormFactor(): FormFactor = myFormFactor
    override fun isLibrary(): Boolean = myIsLibrary
    override fun getIcon(): Icon? = myIcon
    override fun getName(): String = myName
    override fun getDescription(): String? = myDescription
    override fun toString(): String = name

    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      return ConfigureAndroidModuleStep(model, myFormFactor, myMinSdkLevel, basePackage, myName)
    }
  }
}

private fun getModuleTypeIcon(templateFile: File): Icon? =
  getTemplateIcon(TemplateHandle(templateFile), false)
