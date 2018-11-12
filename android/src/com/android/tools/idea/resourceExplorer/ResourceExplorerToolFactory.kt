/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.resourceExplorer.editor.ResourceExplorer
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
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import org.jetbrains.android.facet.AndroidFacet

private const val TOOL_WINDOW_ID = "Resources Explorer"

private const val STRIPE_TITLE = "Resources"

/**
 * Provides the tool explorer panel
 */
class ResourceExplorerToolFactory : ToolWindowFactory, DumbAware, Condition<Any> {

  override fun isDoNotActivateOnStart(): Boolean = true

  override fun init(window: ToolWindow?) {
    window?.stripeTitle = STRIPE_TITLE
    window?.setDefaultContentUiType(ToolWindowContentUiType.COMBO)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val facet: AndroidFacet? = findCurrentFacet(project)
    if (facet != null) {
      displayInToolWindow(facet, toolWindow)
      val connection = project.messageBus.connect(project)
      connection.subscribe(ToolWindowManagerListener.TOPIC, MyToolWindowManagerListener(project))
    }
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
    facet = ModuleManager.getInstance(project).modules
      .asSequence()
      .mapNotNull { it.androidFacet }
      .firstOrNull()
  }
  return facet
}

private fun displayInToolWindow(facet: AndroidFacet, toolWindow: ToolWindow) {
  val resourceExplorer = ResourceExplorer.createForToolWindow(facet)
  val contentManager = toolWindow.contentManager
  contentManager.removeAllContents(true)
  val content = contentManager.factory.createContent(resourceExplorer, facet.module.name, false)
  Disposer.register(content, resourceExplorer)
  contentManager.addContent(content)
  connectListeners(toolWindow, facet.module.project, resourceExplorer)
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
    val window: ToolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
    val contentManager = window.contentManager
    val resourceExplorerIsPresent = contentManager.contents.any { it.component is ResourceExplorer }
    if (!window.isVisible) {
      contentManager.removeAllContents(true)
    }
    else if (!resourceExplorerIsPresent) {
      val facet = findCurrentFacet(project)
      if (facet != null) {
        displayInToolWindow(facet, window)
      }
    }
  }
}
