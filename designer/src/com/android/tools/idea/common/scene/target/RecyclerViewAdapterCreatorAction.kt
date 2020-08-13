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
package com.android.tools.idea.common.scene.target

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ChooseCustomFragmentTemplatesStep
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.impl.xml.recycleradapter.currentRecyclerViewLayout
import com.android.tools.idea.wizard.template.impl.xml.recycleradapter.recyclerViewAdapterFragmentTemplate
import com.android.tools.idea.wizard.template.impl.xml.recycleradapter.recyclerViewAdapterNoFragmentTemplate
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.GENERATE_RECYCLER
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

class RecyclerViewAdapterCreatorAction @JvmOverloads constructor(
  assistantLabel: String = "Generate Recycler View Adapter"
) : DirectViewAction(null, assistantLabel) {

  override fun perform(editor: ViewEditor,
                       handler: ViewHandler,
                       component: NlComponent,
                       selectedChildren: MutableList<NlComponent>,
                       modifiers: Int) {
    showWizardWithOptions(component, "Choose the adapter option", "Choose the adapter option")
  }

  /**
   * Trying to show multiple options at the first wizard.
   */
  private fun showWizardWithOptions(component: NlComponent,
                                    commandName: String,
                                    dialogTitle: String) {
    val projectSyncInvoker: ProjectSyncInvoker = DefaultProjectSyncInvoker()
    val facet = AndroidFacet.getInstance(component.model.module)!!
    val project = facet.module.project
    val targetDirectory = getTargetDirectory(component)
    val moduleTemplates = facet.getModuleTemplates(targetDirectory)
    val initialPackageSuggestion = facet.getPackageForPath(moduleTemplates, targetDirectory)

    assert(moduleTemplates.isNotEmpty())

    val renderModel = fromFacet(
      facet, initialPackageSuggestion, moduleTemplates[0], commandName,
      projectSyncInvoker, true, GENERATE_RECYCLER)

    // Remove ".xml" from the name.
    currentRecyclerViewLayout = component.model.file.name
    currentRecyclerViewLayout = currentRecyclerViewLayout.substring(0, currentRecyclerViewLayout.length - 4)

    val template = listOf(
      recyclerViewAdapterNoFragmentTemplate,
      recyclerViewAdapterFragmentTemplate)

    val chooseTypeStep: SkippableWizardStep<*> = ChooseCustomFragmentTemplatesStep(renderModel, targetDirectory, template)

    val wizardBuilder = ModelWizard.Builder().apply {
      addStep(chooseTypeStep)
    }

    // Must be launched from dispatch thread.
    ApplicationManager.getApplication().invokeLater {
      StudioWizardDialogBuilder(wizardBuilder.build(), dialogTitle).setProject(project).build().show()
    }
  }

  private fun getTargetDirectory(component: NlComponent): VirtualFile {
    val targetFile = component.model.file
    var targetDirectory = targetFile.virtualFile
    if (!targetFile.isDirectory) {
      targetDirectory = targetFile.parent?.virtualFile ?: targetFile.virtualFile
      assert(targetDirectory != null)
    }
    return targetDirectory
  }
}

