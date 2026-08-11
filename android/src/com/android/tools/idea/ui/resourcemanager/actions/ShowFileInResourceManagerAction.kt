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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.res.isLocalResourceDirectory
import com.android.tools.idea.ui.resourcemanager.MANAGER_SUPPORTED_RESOURCES
import com.android.tools.idea.ui.resourcemanager.RESOURCE_EXPLORER_TOOL_WINDOW_ID
import com.android.tools.idea.ui.resourcemanager.ResourceExplorer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.android.facet.AndroidFacet

/**
 * Opens the [ResourceExplorer] and select the current [VirtualFile] if available and is under an Android Res directory.
 *
 * This action replaces the `ShowThumbnailsAction` when the file is an Android Res directory, otherwise it delegates the event to the
 * `ShowThumbnailsAction`
 */
class ShowFileInResourceManagerAction : DumbAwareAction("Show In Resource Manager", "Display selected file in the Resource Manager", null) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

    ReadAction.nonBlocking<AndroidFacet?> {
        if (isSupportedInResManager(file, project)) {
          AndroidFacet.getInstance(file, project)
        } else {
          null
        }
      }
      .expireWith(project)
      .finishOnUiThread(ModalityState.defaultModalityState()) { facet ->
        if (facet != null) {
          showResourceExplorer(project, file, facet)
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val isSupported = isSupportedInResManager(file, project)
    if (e.isFromContextMenu) {
      // Popups should only show enabled actions.
      e.presentation.isEnabledAndVisible = isSupported
    } else {
      e.presentation.isEnabled = isSupported
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private fun showResourceExplorer(project: Project, file: VirtualFile, facet: AndroidFacet) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID) ?: return
    toolWindow.show {
      val resourceExplorer = toolWindow.contentManager.getContent(0)?.component as? ResourceExplorer
      resourceExplorer?.selectAsset(facet, file)
    }
  }

  @RequiresBackgroundThread
  private fun isSupportedInResManager(file: VirtualFile?, project: Project?): Boolean {
    if (file == null || project == null) {
      return false
    }
    // Check that the resource manager is already available
    if (ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID)?.isAvailable != true) return false

    val dir = if (file.isDirectory) file else file.parent ?: return false

    // Check if dir itself is a resource directory
    if (isLocalResourceDirectory(dir, project)) {
      return true
    }

    // Check if dir is a resource subdirectory
    var parent = dir.parent ?: return false
    if (parent.name == "default") {
      parent = parent.parent ?: return false
    }

    return isLocalResourceDirectory(parent, project) && isSupportedResource(dir)
  }

  private fun isSupportedResource(file: VirtualFile): Boolean {
    val folderName = ResourceFolderType.getFolderType(file.name)?.getName() ?: return false
    return ResourceType.fromFolderName(folderName) in MANAGER_SUPPORTED_RESOURCES
  }
}
