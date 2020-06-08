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
package com.android.tools.idea.npw.dynamicapp

import com.android.tools.idea.npw.model.NewProjectModel.Companion.getSuggestedProjectPackage
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class NewDynamicAppModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> =
    listOf(FeatureTemplateGalleryEntry(), InstantFeatureTemplateGalleryEntry())

  private class FeatureTemplateGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.DynamicFeatureModule
    override val name: String = message("android.wizard.module.new.dynamic.module")
    override val description: String = message("android.wizard.module.new.dynamic.module.description")

    override fun toString() = name
    override fun createStep(project: Project, projectSyncInvoker: ProjectSyncInvoker, moduleParent: String?): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      return ConfigureDynamicModuleStep(DynamicFeatureModel(project, projectSyncInvoker, false, name, description), basePackage)
    }
  }

  private class InstantFeatureTemplateGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.InstantDynamicFeatureModule
    override val name: String = message("android.wizard.module.new.dynamic.module.instant")
    override val description: String = message("android.wizard.module.new.dynamic.module.instant.description")

    override fun toString() = name
    override fun createStep(project: Project, projectSyncInvoker: ProjectSyncInvoker, moduleParent: String?): SkippableWizardStep<*> {
      val basePackage = getSuggestedProjectPackage()
      return ConfigureDynamicModuleStep(DynamicFeatureModel(project, projectSyncInvoker, true, name, description), basePackage)
    }
  }
}
