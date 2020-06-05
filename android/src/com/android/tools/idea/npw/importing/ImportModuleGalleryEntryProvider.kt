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

import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class ImportModuleGalleryEntryProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOf(
    EclipseImportModuleGalleryEntry(),
    GradleImportModuleGalleryEntry(),
    ArchiveImportModuleGalleryEntry()
  )
  private class EclipseImportModuleGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.EclipseModule
    override val name: String = message("android.wizard.module.import.eclipse.title")
    override val description: String = message("android.wizard.module.import.eclipse.description")
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> =
      SourceToGradleModuleStep(SourceToGradleModuleModel(project, projectSyncInvoker))
  }

  private class GradleImportModuleGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.GradleModule
    override val name: String = message("android.wizard.module.import.gradle.title")
    override val description: String = message("android.wizard.module.import.gradle.description")
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> =
      SourceToGradleModuleStep(SourceToGradleModuleModel(project, projectSyncInvoker))
  }

  private class ArchiveImportModuleGalleryEntry : ModuleGalleryEntry {
    override val icon: Icon = AndroidIcons.Wizards.AndroidModule
    override val name: String = message("android.wizard.module.import.archive.title")
    override val description: String = message("android.wizard.module.import.archive.description")
    override fun createStep(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): SkippableWizardStep<*> =
      ArchiveToGradleModuleStep(ArchiveToGradleModuleModel(project, projectSyncInvoker))
  }
}