/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.actions

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.RESOURCE_EXPLORER_TOOL_WINDOW_ID
import com.android.tools.idea.resourceExplorer.editor.ResourceExplorer
import com.android.tools.idea.resourceExplorer.editor.SUPPORTED_RESOURCES
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidResourceUtil

/**
 * Opens the [ResourceExplorer] and select the current [VirtualFile] if available and is
 * under an Android Res directory.
 *
 * This action replaces the [org.intellij.images.actions.ShowThumbnailsAction] when the file
 * is an Android Res directory, otherwise it delegates the event to the
 * [org.intellij.images.actions.ShowThumbnailsAction]
 */
class ShowFileInResourceManagerAction
  : DumbAwareAction("Show In Resource Manager",
                    "Display selected file in the Resource Manager", null) {

  private val showThumbnailAction: AnAction? = ActionManager.getInstance().getAction("Images.ShowThumbnails")

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    if (project != null && file != null
        && isSupportedInResManager(file, project)) {
      showResourceExplorer(project, file)
      return
    }
    showThumbnailAction?.actionPerformed(e)
  }

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (isSupportedInResManager(file, project)) {
      if (ActionPlaces.isPopupPlace(e.place)) {
        e.presentation.isEnabledAndVisible = true
      }
      else {
        e.presentation.isEnabled = true
      }
      e.presentation.icon = StudioIcons.Shell.ToolWindows.VISUAL_ASSETS
      return
    }
    if (showThumbnailAction != null) {
      e.presentation.icon = showThumbnailAction.templatePresentation.icon
      showThumbnailAction.update(e)
      e.presentation.text = showThumbnailAction.templatePresentation.text
    }
  }

  private fun showResourceExplorer(project: Project, file: VirtualFile) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID)
    val facet = AndroidFacet.getInstance(file, project)
    if (facet != null) {
      toolWindow.show {
        val resourceExplorer = toolWindow.contentManager.getContent(0)?.component as? ResourceExplorer
        resourceExplorer?.selectAsset(facet, file)
      }
    }
  }

  private fun isSupportedInResManager(file: VirtualFile?, project: Project?): Boolean {
    if (file == null || project == null) {
      return false
    }
    val dir = getPsiDir(file, project) ?: return false
    return AndroidResourceUtil.isResourceDirectory(dir)
           || AndroidResourceUtil.isResourceSubdirectory(dir, null)
           && isSupportedResource(dir.virtualFile)
  }

  private fun isSupportedResource(file: VirtualFile): Boolean {
    val folderName = ResourceFolderType.getFolderType(file.name)?.getName() ?: return false
    return ResourceType.fromFolderName(folderName) in SUPPORTED_RESOURCES
  }

  private fun getPsiDir(file: VirtualFile, project: Project): PsiDirectory? = if (file.isDirectory) {
    PsiManager.getInstance(project).findDirectory(file)
  }
  else {
    PsiManager.getInstance(project).findFile(file)?.containingDirectory
  }
}