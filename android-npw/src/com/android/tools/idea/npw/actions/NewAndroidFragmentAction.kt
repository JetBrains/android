/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.actions

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ChooseFragmentTypeStep
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.FRAGMENT_GALLERY
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.io.File

/**
 * An action to launch the fragment wizard to create a fragment from a template.
 */
class NewAndroidFragmentAction
  : AnAction(AndroidBundle.message("android.wizard.new.fragment.title"),
             AndroidBundle.message("android.wizard.new.fragment.title"),
             null) {

  private var shouldOpenFiles = true

  init {
    templatePresentation.icon = StudioIcons.Shell.Filetree.ANDROID_FILE
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val module = PlatformCoreDataKeys.MODULE.getData(dataContext) ?: return
    val facet = AndroidFacet.getInstance(module)
    val targetDirectory = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    if (facet == null || AndroidModel.get(facet) == null || targetDirectory == null) {
      return
    }
    val createdFiles = dataContext.getData(CREATED_FILES)
    openNewFragmentWizard(facet, module.project, targetDirectory, createdFiles, shouldOpenFiles)
  }

  companion object {
    fun openNewFragmentWizard(facet: AndroidFacet,
                              project: Project,
                              targetDirectory: VirtualFile,
                              createdFiles: MutableList<File>?,
                              shouldOpenFiles: Boolean) {
      if (AndroidModel.get(facet) == null) {
        return
      }

      // If the user selected a simulated folder entry (eg "Manifests"), there will be no target directory
      val directory = if (!targetDirectory.isDirectory) targetDirectory.parent else targetDirectory

      val moduleTemplates = facet.getModuleTemplates(targetDirectory)
      assert(moduleTemplates.isNotEmpty())

      val initialPackageSuggestion = facet.getPackageForPath(moduleTemplates, targetDirectory)

      val dialogTitle = AndroidBundle.message("android.wizard.new.fragment.title")

      val projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
      val renderModel = RenderTemplateModel.fromFacet(
        facet, initialPackageSuggestion, moduleTemplates[0], AndroidBundle.message("android.wizard.fragment.add", FormFactor.MOBILE.id),
        projectSyncInvoker, shouldOpenFiles, FRAGMENT_GALLERY)

      val fragmentTypeStep = ChooseFragmentTypeStep(renderModel, FormFactor.MOBILE, directory)
      val wizard = ModelWizard.Builder().addStep(fragmentTypeStep).build()

      val dialog = StudioWizardDialogBuilder(wizard, dialogTitle).setProject(project).build()
      dialog.show()
      createdFiles?.addAll(renderModel.createdFiles)
    }
  }
}

