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
package com.android.tools.idea.npw.java

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.multiplatform.ConfigureKotlinMultiplatformLibraryModuleStep
import com.android.tools.idea.npw.multiplatform.NewKotlinMultiplatformLibraryModuleModel
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

class NewLibraryModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOfNotNull(
    JavaModuleTemplateGalleryEntry(),
    if (StudioFlags.NPW_NEW_KOTLIN_MULTIPLATFORM_MODULE.get()) KotlinMultiplatformLibraryModuleTemplateGalleryEntry() else null,
  )

  private class JavaModuleTemplateGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = KotlinIcons.SMALL_LOGO
    override val name: String = message("android.wizard.module.new.java.or.kotlin.library")
    override val description: String = message("android.wizard.module.new.java.or.kotlin.library.description")
    override fun toString() = name
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> =
      ConfigureLibraryModuleStep(NewLibraryModuleModel(project, moduleParent, projectSyncInvoker), name)
  }

  private class KotlinMultiplatformLibraryModuleTemplateGalleryEntry : ModuleGalleryEntry {
    override val name: String = message("android.wizard.module.new.kotlin.multiplatform.library")
    override val description: String = message("android.wizard.module.new.kotlin.multiplatform.library.description")
    override val icon: Icon = KotlinIcons.SMALL_LOGO

    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> {
      return ConfigureKotlinMultiplatformLibraryModuleStep(
        NewKotlinMultiplatformLibraryModuleModel(project, moduleParent, projectSyncInvoker), name)
    }
  }
}