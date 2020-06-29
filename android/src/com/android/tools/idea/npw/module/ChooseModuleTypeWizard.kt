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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import com.android.tools.idea.npw.module.deprecated.ChooseModuleTypeStep as DeprecatedChooseModuleTypeStep

/**
 * This step allows the user to select which type of module they want to create.
 */
class ChooseModuleTypeWizard(
  private val project: Project,
  private val moduleParent: String,
  private val moduleGalleryEntries: List<ModuleGalleryEntry>,
  private val projectSyncInvoker: ProjectSyncInvoker
) {
  // TODO: Create the left list, and connect its entries to the wizard dialog
}

@VisibleForTesting
fun sortModuleEntries(moduleTypeProviders: List<ModuleGalleryEntry>): List<ModuleGalleryEntry> {
  // To have a sequence specified by design, we hardcode the sequence. Everything else is added at the end (sorted by name)
  val orderedNames = arrayOf(
    message("android.wizard.module.new.mobile"),
    message("android.wizard.module.new.library"),
    message("android.wizard.module.new.dynamic.module"),
    message("android.wizard.module.new.dynamic.module.instant"),
    message("android.wizard.module.new.automotive"),
    message("android.wizard.module.new.wear"),
    message("android.wizard.module.new.tv"),
    message("android.wizard.module.new.things"),
    message("android.wizard.module.import.gradle.title"),
    message("android.wizard.module.import.eclipse.title"),
    message("android.wizard.module.import.archive.title"),
    message("android.wizard.module.new.java.or.kotlin.library"),
    message("android.wizard.module.new.google.cloud"),
    message("android.wizard.module.new.benchmark.module.app"))

  return moduleTypeProviders.partition { it.name in orderedNames }.run {
    first.sortedBy { orderedNames.indexOf(it.name) } + second.sortedBy { it.name }
  }
}

fun createWithDefaultGallery(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker): ModelWizardStep<out WizardModel>  {
  val moduleDescriptions = ModuleDescriptionProvider.EP_NAME.extensions.flatMap { it.getDescriptions(project) }
  return DeprecatedChooseModuleTypeStep(project, moduleParent, moduleDescriptions, projectSyncInvoker)
}

fun showDefaultWizard(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker) {
  val moduleDescriptions = ModuleDescriptionProvider.EP_NAME.extensions.flatMap { it.getDescriptions(project) }
  if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) {
    // TODO
  }

  val chooseModuleTypeStep = DeprecatedChooseModuleTypeStep(project, moduleParent, moduleDescriptions, projectSyncInvoker)
  val wizard = ModelWizard.Builder().addStep(chooseModuleTypeStep).build()
  StudioWizardDialogBuilder(wizard, message("android.wizard.module.new.module.title")).build().show()
}
