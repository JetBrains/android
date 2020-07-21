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
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import icons.AndroidIcons
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class NewAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOf(
    MobileModuleTemplateGalleryEntry(),
    AndroidLibraryModuleTemplateGalleryEntry(),
    WearModuleTemplateGalleryEntry(),
    TvModuleTemplateGalleryEntry(),
    ThingsModuleTemplateGalleryEntry(),
    AutomotiveModuleTemplateGalleryEntry()
  )

  private abstract class AndroidModuleTemplateGalleryEntry(
    override val name: String,
    override val description: String,
    override val icon: Icon,
    val formFactor: FormFactor
  ): ModuleGalleryEntry {
    val isLibrary = false

    override fun toString(): String = name
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidModuleModel.fromExistingProject(project, moduleParent, projectSyncInvoker, createDummyTemplate(), isLibrary)
      return ConfigureAndroidModuleStep(model, formFactor, LOWEST_ACTIVE_API, basePackage, name)
    }
  }

  private class MobileModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.mobile"),
    message("android.wizard.module.new.mobile.description"),
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Avd.DEVICE_MOBILE else AndroidIcons.Wizards.MobileModule,
    FormFactor.MOBILE
  )

  private class AutomotiveModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.automotive"),
    message("android.wizard.module.new.automotive.description"),
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Avd.DEVICE_AUTOMOTIVE else AndroidIcons.Wizards.AutomotiveModule,
    FormFactor.AUTOMOTIVE
  )

  private class ThingsModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.things"),
    message("android.wizard.module.new.things.description"),
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Shell.Filetree.ANDROID_PROJECT else AndroidIcons.Wizards.ThingsModule,
    FormFactor.THINGS
  )

  private class TvModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.tv"),
    message("android.wizard.module.new.tv.description"),
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Avd.DEVICE_TV else AndroidIcons.Wizards.TvModule,
    FormFactor.TV
  )

  private class WearModuleTemplateGalleryEntry : AndroidModuleTemplateGalleryEntry(
    message("android.wizard.module.new.wear"),
    message("android.wizard.module.new.wear.description"),
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Avd.DEVICE_WEAR else AndroidIcons.Wizards.WearModule,
    FormFactor.WEAR
  )

  private class AndroidLibraryModuleTemplateGalleryEntry: ModuleGalleryEntry {
    override val name: String = message("android.wizard.module.new.library")
    override val description: String = message("android.wizard.module.new.library.description")
    override val icon: Icon = if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) StudioIcons.Shell.Filetree.ANDROID_PROJECT else AndroidIcons.Wizards.AndroidModule

    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      val model = NewAndroidModuleModel.fromExistingProject(project, moduleParent, projectSyncInvoker, createDummyTemplate(), true)
      return ConfigureAndroidModuleStep(model, FormFactor.MOBILE, LOWEST_ACTIVE_API, basePackage, name)
    }
  }
}

