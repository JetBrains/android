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

import org.jetbrains.android.util.AndroidBundle.message

import com.android.tools.adtui.ASGallery
import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

/**
 * This step allows the user to select which type of module they want to create.
 */
class ChooseModuleTypeStep(private val myProject: Project,
                           private val myModuleParent: String?,
                           moduleGalleryEntries: List<ModuleGalleryEntry>,
                           private val myProjectSyncInvoker: ProjectSyncInvoker) : ModelWizardStep.WithoutModel(
  message("android.wizard.module.new.module.header")) {

  private val myModuleGalleryEntryList: List<ModuleGalleryEntry> = sortModuleEntries(moduleGalleryEntries)
  private val myRootPanel: JComponent = createGallery().also {
    FormScalingUtil.scaleComponentTree(this.javaClass, it)
  }

  private var myFormFactorGallery: ASGallery<ModuleGalleryEntry>? = null
  private var myModuleDescriptionToStepMap: MutableMap<ModuleGalleryEntry, SkippableWizardStep<*>>? = null

  override fun getComponent(): JComponent = myRootPanel

  public override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    val allSteps = arrayListOf<ModelWizardStep<*>>()
    myModuleDescriptionToStepMap = hashMapOf()
    for (moduleGalleryEntry in myModuleGalleryEntryList) {
      val model = NewModuleModel(myProject, myModuleParent, myProjectSyncInvoker, createDummyTemplate())
      if (moduleGalleryEntry is ModuleTemplateGalleryEntry) {
        model.isLibrary.set(moduleGalleryEntry.isLibrary)
        model.templateFile.value = moduleGalleryEntry.templateFile
      }

      val step = moduleGalleryEntry.createStep(model)
      allSteps.add(step)
      myModuleDescriptionToStepMap!![moduleGalleryEntry] = step
    }

    return allSteps
  }

  private fun createGallery(): JComponent {
    myFormFactorGallery = WizardGallery(
      title,
      { galEntry -> galEntry?.icon },
      { galEntry -> galEntry?.name ?: message("android.wizard.gallery.item.none") }
    )

    return JBScrollPane(myFormFactorGallery)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    myFormFactorGallery!!.model = JBList.createDefaultListModel<Any>(*myModuleGalleryEntryList.toTypedArray())
    myFormFactorGallery!!.setDefaultAction(object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        wizard.goForward()
      }
    })

    myFormFactorGallery!!.selectedIndex = 0
  }

  override fun onProceeding() {
    // This wizard includes a step for each module, but we only visit the selected one. First, we hide all steps (in case we visited a
    // different module before and hit back), and then we activate the step we care about.
    val selectedEntry = myFormFactorGallery!!.selectedElement
    myModuleDescriptionToStepMap!!.forEach { (galleryEntry, step) -> step.setShouldShow(galleryEntry === selectedEntry) }
  }

  override fun getPreferredFocusComponent(): JComponent? {
    return myFormFactorGallery
  }

  companion object {
    const val ANDROID_AUTOMOTIVE_MODULE_NAME = "Automotive Module"
    const val ANDROID_WEAR_MODULE_NAME = "Wear OS Module"
    const val ANDROID_TV_MODULE_NAME = "Android TV Module"
    const val ANDROID_THINGS_MODULE_NAME = "Android Things Module"
    const val JAVA_LIBRARY_MODULE_NAME = "Java Library"
    const val GOOGLE_CLOUD_MODULE_NAME = "Google Cloud Module"

    @JvmStatic
    fun createWithDefaultGallery(project: Project, moduleGroup: String?,
                                 projectSyncInvoker: ProjectSyncInvoker): ChooseModuleTypeStep {
      val moduleDescriptions = arrayListOf<ModuleGalleryEntry>()
      for (provider in ModuleDescriptionProvider.EP_NAME.extensions) {
        moduleDescriptions.addAll(provider.getDescriptions(project))
      }
      return ChooseModuleTypeStep(project, moduleGroup, moduleDescriptions, projectSyncInvoker)
    }

    @VisibleForTesting
    fun sortModuleEntries(moduleTypesProviders: List<ModuleGalleryEntry>): List<ModuleGalleryEntry> {
      // To have a sequence specified by design, we hardcode the sequence. Everything else is added at the end (sorted by name)
      val orderedNames = arrayOf(message("android.wizard.module.new.mobile"), message("android.wizard.module.new.library"),
                                 message("android.wizard.module.new.dynamic.module"),
                                 message("android.wizard.module.new.dynamic.module.instant"), ANDROID_AUTOMOTIVE_MODULE_NAME,
                                 ANDROID_WEAR_MODULE_NAME, ANDROID_TV_MODULE_NAME, ANDROID_THINGS_MODULE_NAME,
                                 message("android.wizard.module.import.gradle.title"),
                                 message("android.wizard.module.import.eclipse.title"), message("android.wizard.module.import.title"),
                                 JAVA_LIBRARY_MODULE_NAME, GOOGLE_CLOUD_MODULE_NAME,
                                 message("android.wizard.module.new.benchmark.module.app"))
      val entryMap = moduleTypesProviders.associateBy { it.name }.toMutableMap()

      val result = arrayListOf<ModuleGalleryEntry>()
      for (name in orderedNames) {
        val entry = entryMap.remove(name)
        if (entry != null) {
          result.add(entry)
        }
      }

      val secondHalf = entryMap.values.sortedBy { it.name }

      result.addAll(secondHalf)
      return result
    }
  }
}
