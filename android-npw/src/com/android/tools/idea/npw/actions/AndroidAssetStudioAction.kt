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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.io.File
import java.net.URL

/**
 * Action to invoke one of the Asset Studio wizards.
 *
 * This action is visible anywhere within a module that has an Android facet.
 */
abstract class AndroidAssetStudioAction(
  text: String?,
  description: String?,
) : AnAction(text, description, StudioIcons.Common.ANDROID_HEAD) {

  override fun update(event: AnActionEvent) {
    event.presentation.setVisible(isAvailable(event))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(event: AnActionEvent) {
    if (event.getData(LangDataKeys.IDE_VIEW) == null) return
    val module = event.getData(PlatformCoreDataKeys.MODULE) ?: return
    val location = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val facet = AndroidFacet.getInstance(module) ?: return
    val template: NamedModuleTemplate = getModuleTemplate(module, location) ?: return
    val resFolder: File = findClosestResFolder(template.paths, location) ?: return

    val wizard = createWizard(facet, template, resFolder)
    val dialogBuilder = StudioWizardDialogBuilder(wizard, "Asset Studio")
    dialogBuilder.setProject(facet.module.project)
      .setMinimumSize(wizardMinimumSize)
      .setPreferredSize(wizardPreferredSize)
      .setHelpUrl(helpUrl)
    dialogBuilder.build().show()
  }

  /**
   * Creates a wizard to show or returns `null` if the showing of a wizard should be aborted.
   * If a subclass class aborts showing the wizard, it should still give some visual indication,
   * such as an error dialog.
   */
  protected abstract fun createWizard(facet: AndroidFacet, template: NamedModuleTemplate, resFolder: File): ModelWizard

  protected abstract val wizardMinimumSize: Dimension

  protected abstract val wizardPreferredSize: Dimension

  protected open val helpUrl: URL?
    get() = null
}

private fun isAvailable(event: AnActionEvent): Boolean {
  val module = event.getData(PlatformCoreDataKeys.MODULE) ?: return false
  val view = event.getData(LangDataKeys.IDE_VIEW) ?: return false
  val location = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return false

  return view.directories.size > 0 && AndroidFacet.getInstance(module) != null &&
         module.project.getProjectSystem().allowsFileCreation() && getModuleTemplate(module, location) != null
}

private fun getModuleTemplate(module: Module, location: VirtualFile): NamedModuleTemplate? {
  for (namedTemplate in module.getModuleSystem().getModuleTemplates(location)) {
    if (!namedTemplate.paths.resDirectories.isEmpty()) {
      return namedTemplate
    }
  }
  return null
}

private fun findClosestResFolder(paths: AndroidModulePaths, location: VirtualFile): File? {
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
  return bestMatch
}
