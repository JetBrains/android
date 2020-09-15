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

package com.android.tools.idea.npw.module.deprecated

import com.android.tools.adtui.ASGallery
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

/**
 * This step allows the user to select which type of module they want to create.
 */
@Deprecated("Use new ChooseModuleTypeStep", ReplaceWith("ChooseModuleTypeStep", "com.android.tools.idea.npw.module.ChooseModuleTypeStep"))
class ChooseModuleTypeStep(
  private val project: Project,
  private val moduleParent: String,
  moduleGalleryEntries: List<ModuleGalleryEntry>,
  private val projectSyncInvoker: ProjectSyncInvoker
) : ModelWizardStep.WithoutModel(message("android.wizard.module.new.module.header")) {
  private val moduleGalleryEntryList: List<ModuleGalleryEntry> = sortModuleEntries(moduleGalleryEntries)
  private var formFactorGallery: ASGallery<ModuleGalleryEntry> =
    WizardGallery(title, { it?.icon }, { it?.name ?: message("android.wizard.gallery.item.none") })
  private val rootPanel: JComponent = JBScrollPane(formFactorGallery).also {
    FormScalingUtil.scaleComponentTree(this.javaClass, it)
  }
  private val moduleDescriptionToStepMap = hashMapOf<ModuleGalleryEntry, SkippableWizardStep<*>>()
  private val logger: Logger get() = logger<ChooseModuleTypeStep>()

  override fun getComponent(): JComponent = rootPanel

  public override fun createDependentSteps(): Collection<ModelWizardStep<*>> = moduleGalleryEntryList.mapNotNull {
    try {
      it.createStep(project, moduleParent, projectSyncInvoker).also { step ->
        moduleDescriptionToStepMap[it] = step
      }
    } catch (ex: Throwable) {
      logger.error(ex)
      null
    }
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    formFactorGallery.model = JBList.createDefaultListModel<Any>(*moduleGalleryEntryList.toTypedArray())
    formFactorGallery.setDefaultAction(object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        wizard.goForward()
      }
    })

    formFactorGallery.selectedIndex = 0
  }

  override fun onProceeding() {
    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    val selectedEntry = formFactorGallery.selectedElement
    moduleDescriptionToStepMap.forEach { (galleryEntry, step) -> step.setShouldShow(galleryEntry === selectedEntry) }
  }

  override fun getPreferredFocusComponent(): JComponent? = formFactorGallery
}

private fun sortModuleEntries(moduleTypesProviders: List<ModuleGalleryEntry>): List<ModuleGalleryEntry> {
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
    message("android.wizard.module.new.java.or.kotlin.library"),
    message("android.wizard.module.new.google.cloud"),
    message("android.wizard.module.new.benchmark.module.app"),
    message("android.wizard.module.new.native.library")
  )

  return moduleTypesProviders.partition { it.name in orderedNames }.run {
    first.sortedBy { orderedNames.indexOf(it.name) } + second.sortedBy { it.name }
  }
}
