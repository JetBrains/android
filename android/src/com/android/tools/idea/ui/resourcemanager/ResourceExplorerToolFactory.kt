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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.ui.resourcemanager.editor.ResourceExplorer
import com.android.tools.idea.ui.resourcemanager.view.NoFacetView
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import org.jetbrains.android.facet.AndroidFacet

const val RESOURCE_EXPLORER_TOOL_WINDOW_ID = "Resources Explorer"

private const val STRIPE_TITLE = "Resource Manager"

/**
 * Provides the tool explorer panel
 */
class ResourceExplorerToolFactory : ToolWindowFactory, DumbAware, Condition<Any> {

  override fun isDoNotActivateOnStart(): Boolean = true

  override fun init(window: ToolWindow?) {
    window?.stripeTitle = STRIPE_TITLE
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    createContent(toolWindow, project)
    project.messageBus.connect(project).subscribe(ToolWindowManagerListener.TOPIC, MyToolWindowManagerListener(project))
  }

  override fun shouldBeAvailable(project: Project) = StudioFlags.RESOURCE_MANAGER_ENABLED.get()

  /**
   * Implementation of [Condition].
   */
  override fun value(o: Any) = StudioFlags.RESOURCE_MANAGER_ENABLED.get()
}

private fun connectListeners(toolWindow: ToolWindow,
                             project: Project,
                             resourceExplorer: ResourceExplorer) {
  val connection = project.messageBus.connect(resourceExplorer)
  connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorListener(project, toolWindow, resourceExplorer))
  connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, SyncResultListener(project, resourceExplorer, toolWindow))
}

/**
 * Find the facet corresponding to the current opened editor if any, otherwise returns the
 * facet of the first Android module if any is found.
 */
private fun findCurrentFacet(project: Project): AndroidFacet? {
  var facet: AndroidFacet?

  // Find facet for opened file
  facet = FileEditorManager.getInstance(project).selectedFiles
    .asSequence()
    .mapNotNull { ModuleUtilCore.findModuleForFile(it, project) }
    .mapNotNull { it.androidFacet }
    .firstOrNull()

  // If no facet has been found, find the first project's module with a facet
  if (facet == null) {
    facet = findAllFacets(project)
      .firstOrNull()
  }
  return facet
}

private fun findAllFacets(project: Project): Sequence<AndroidFacet> {
  return ModuleManager.getInstance(project).modules
    .asSequence()
    .mapNotNull { it.androidFacet }
}

private fun createContent(toolWindow: ToolWindow,
                          project: Project) {
  toolWindow.contentManager.removeAllContents(true)
  val facet = findCurrentFacet(project)
  if (facet != null) {
    displayInToolWindow(facet, toolWindow)
  }
  else {
    displayNoFacetView(project, toolWindow)
  }
}

private fun displayNoFacetView(project: Project, toolWindow: ToolWindow) {
  val contentManager = toolWindow.contentManager
  val content = contentManager.factory.createContent(NoFacetView(project), null, false)
  contentManager.addContent(content)
}

private fun displayInToolWindow(facet: AndroidFacet, toolWindow: ToolWindow) {
  val resourceExplorer = ResourceExplorer.createForToolWindow(facet)
  val contentManager = toolWindow.contentManager
  contentManager.removeAllContents(true)
  val content = contentManager.factory.createContent(resourceExplorer, null, false)
  Disposer.register(content, resourceExplorer)
  contentManager.addContent(content)
  connectListeners(toolWindow, facet.module.project, resourceExplorer)
  ResourceManagerTracking.logPanelOpens()
}

private class MyFileEditorListener(val project: Project,
                                   val toolWindow: ToolWindow,
                                   val resourceExplorer: ResourceExplorer?) : FileEditorManagerListener {

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val editor = event.newEditor ?: return
    editorFocused(editor, project, resourceExplorer)
  }

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    editorFocused(source.getSelectedEditor(file) ?: return, project, resourceExplorer)
  }

  private fun editorFocused(
    editor: FileEditor,
    project: Project,
    resourceExplorer: ResourceExplorer?
  ) {
    val module = editor.file?.let {
      ModuleUtilCore.findModuleForFile(it, project)
    } ?: return

    toolWindow.contentManager.getContent(0)?.displayName = module.name
    val facet = AndroidFacet.getInstance(module)
    if (facet != null && facet != resourceExplorer?.facet) {
      resourceExplorer?.facet = facet
    }
  }
}

private class MyToolWindowManagerListener(private val project: Project) : ToolWindowManagerListener {

  override fun stateChanged() {
    val window: ToolWindow = ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID) ?: return
    val contentManager = window.contentManager
    val resourceExplorerIsPresent = contentManager.contents.any { it.component is ResourceExplorer }
    if (!window.isVisible) {
      contentManager.removeAllContents(true)
      ResourceManagerTracking.logPanelCloses()
    }
    else if (!resourceExplorerIsPresent) {
      createContent(window, project)
    }
  }
}

private class SyncResultListener(private val project: Project,
                                 private val resourceExplorer: ResourceExplorer,
                                 private val toolWindow: ToolWindow) : ProjectSystemSyncManager.SyncResultListener {
  override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
    // After sync, if the facet is not found anymore, recreate the view.
    if (!findAllFacets(project).contains(resourceExplorer.facet)) {
      createContent(toolWindow, project)
    }
  }
}
