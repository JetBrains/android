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
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.NewAndroidNativeModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class NewAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOfNotNull(
    MobileModuleTemplateGalleryEntry(),
    AndroidLibraryModuleTemplateGalleryEntry(),
    if (StudioFlags.NPW_NEW_NATIVE_MODULE.get()) AndroidNativeLibraryModuleTemplateGalleryEntry() else null,
    WearModuleTemplateGalleryEntry(),
    TvModuleTemplateGalleryEntry(),
    AutomotiveModuleTemplateGalleryEntry()
  )

  private abstract class AndroidModuleTemplateGalleryEntry(
    override val name: String,
    override val description: String,
    override val icon: Icon,
    val formFactor: FormFactor,
    val category: Category
  ) : ModuleGalleryEntry {
    val isLibrary = false

    override fun toString(): String = name
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidModuleModel.fromExistingProject(project, moduleParent, projectSyncInvoker, formFactor, category, isLibrary)
      return ConfigureAndroidModuleStep(model, LOWEST_ACTIVE_API, basePackage, name)
    }
  }

  private class MobileModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.mobile"),
    message("android.wizard.module.new.mobile.description"),
    StudioIcons.Wizards.Modules.PHONE_TABLET,
    FormFactor.Mobile,
    Category.Activity
  )

  private class AutomotiveModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.automotive"),
    message("android.wizard.module.new.automotive.description"),
    StudioIcons.Wizards.Modules.AUTOMOTIVE,
    FormFactor.Automotive,
    Category.Automotive
  )

  private class TvModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.tv"),
    message("android.wizard.module.new.tv.description"),
    StudioIcons.Wizards.Modules.ANDROID_TV,
    FormFactor.Tv,
    Category.Activity
  )

  private class WearModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.wear"),
    message("android.wizard.module.new.wear.description"),
    StudioIcons.Wizards.Modules.WEAR_OS,
    FormFactor.Wear,
    Category.Wear
  )

  private class AndroidLibraryModuleTemplateGalleryEntry : ModuleGalleryEntry {
    override val name: String = message("android.wizard.module.new.library")
    override val description: String = message("android.wizard.module.new.library.description")
    override val icon: Icon = StudioIcons.Wizards.Modules.ANDROID_LIBRARY

    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidModuleModel.fromExistingProject(
        project = project,
        moduleParent = moduleParent,
        projectSyncInvoker = projectSyncInvoker,
        formFactor = FormFactor.Mobile,
        category = Category.Activity,
        isLibrary = true
      )
      return ConfigureAndroidModuleStep(model, LOWEST_ACTIVE_API, basePackage, name)
    }
  }

  private class AndroidNativeLibraryModuleTemplateGalleryEntry : ModuleGalleryEntry {
    override val name: String = message("android.wizard.module.new.native.library")
    override val description: String = message("android.wizard.module.new.native.library.description")
    override val icon: Icon = StudioIcons.Wizards.Modules.NATIVE

    override fun createStep(project: Project,
                            moduleParent: String,
                            projectSyncInvoker: ProjectSyncInvoker): ConfigureAndroidNativeModuleStep {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidNativeModuleModel(ExistingProjectModelData(project, projectSyncInvoker), moduleParent)
      return ConfigureAndroidNativeModuleStep(model, LOWEST_ACTIVE_API, basePackage, name)
    }
  }
}
