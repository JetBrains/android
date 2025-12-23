/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import java.awt.Dimension
import java.io.File
import java.net.URL
import org.jetbrains.android.facet.AndroidFacet

/**
 * Action to invoke one of the Asset Studio wizards.
 *
 * This action will be visible for modules that have a resource directory as part of the
 * [NamedModuleTemplate]. This will not be available for holder modules.
 */
abstract class AndroidAssetStudioAction(text: String?, description: String?) :
  AnAction(text, description, StudioIcons.Common.ANDROID_HEAD) {

  override fun update(event: AnActionEvent) {
    event.presentation.setVisible(isAvailable(event))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    val context = getAndroidAssetStudioContext(event) ?: return
    val wizard =
      createWizard(
        context.facet,
        context.template,
        VfsUtilCore.virtualToIoFile(context.resVirtualFile),
      )
    showWizard(wizard, context.facet)
  }

  protected open fun showWizard(wizard: ModelWizard, facet: AndroidFacet) {
    val dialogBuilder = StudioWizardDialogBuilder(wizard, "Asset Studio")
    dialogBuilder
      .setProject(facet.module.project)
      .setMinimumSize(wizardMinimumSize)
      .setPreferredSize(wizardPreferredSize)
      .setHelpUrl(helpUrl)
    dialogBuilder.build().show()
  }

  /**
   * Creates a wizard to show or returns `null` if the showing of a wizard should be aborted. If a
   * subclass class aborts showing the wizard, it should still give some visual indication, such as
   * an error dialog.
   */
  protected abstract fun createWizard(
    facet: AndroidFacet,
    template: NamedModuleTemplate,
    resFolder: File,
  ): ModelWizard

  protected abstract val wizardMinimumSize: Dimension

  protected abstract val wizardPreferredSize: Dimension

  protected open val helpUrl: URL?
    get() = null
}

private fun isAvailable(event: AnActionEvent): Boolean {
  val context = getAndroidAssetStudioContext(event) ?: return false
  return context.ideView.directories.isNotEmpty() &&
    context.project.getProjectSystem().allowsFileCreation()
}

private fun getModuleTemplate(project: Project, location: VirtualFile): NamedModuleTemplate? {
  val module = ModuleUtilCore.findModuleForFile(location, project) ?: return null
  for (namedTemplate in module.getModuleSystem().getModuleTemplates(location)) {
    if (!namedTemplate.paths.resDirectories.isEmpty()) {
      return namedTemplate
    }
  }
  return null
}

private fun findClosestResFolder(paths: AndroidModulePaths, location: VirtualFile): VirtualFile? {
  val toFind = location.path
  var bestMatch: File? = null
  var bestCommonPrefixLength = -1
  for (resDir in paths.resDirectories) {
    val commonPrefixLength = StringUtil.commonPrefixLength(resDir.path, toFind)
    if (commonPrefixLength > bestCommonPrefixLength) {
      bestCommonPrefixLength = commonPrefixLength
      bestMatch = resDir
    }
  }
  val resFolder = bestMatch ?: return null

  val local = VfsUtil.findFileByIoFile(resFolder, true)
  if (local != null) return local

  var candidate: VirtualFile? = location
  val resFolderPath = resFolder.path.replace(File.separatorChar, '/')
  while (candidate != null) {
    if (candidate.path == resFolderPath) {
      return candidate
    }
    candidate = candidate.parent
  }
  return null
}

/**
 * Context required by the action to function. [getAndroidAssetStudioContext] will return the
 * context or null if any of the parts are missing. In that case, the action will be disabled.
 */
private data class AndroidAssetStudioContext(
  val project: Project,
  val location: VirtualFile,
  val module: Module,
  val facet: AndroidFacet,
  val template: NamedModuleTemplate,
  val resVirtualFile: VirtualFile,
  val ideView: IdeView,
)

private fun getAndroidAssetStudioContext(event: AnActionEvent): AndroidAssetStudioContext? {
  val view = event.getData(LangDataKeys.IDE_VIEW) ?: return null
  val project = event.getData(CommonDataKeys.PROJECT) ?: return null
  val location = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null

  val template = getModuleTemplate(project, location) ?: return null
  val resVirtualFile = findClosestResFolder(template.paths, location) ?: return null

  val module = ModuleUtilCore.findModuleForFile(resVirtualFile, project) ?: return null
  val facet = AndroidFacet.getInstance(module) ?: return null

  return AndroidAssetStudioContext(project, location, module, facet, template, resVirtualFile, view)
}
