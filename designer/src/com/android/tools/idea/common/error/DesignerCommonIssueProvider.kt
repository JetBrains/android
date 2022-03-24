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
package com.android.tools.idea.common.error

import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

interface DesignerCommonIssueProvider<T> : Disposable {
  var filter: (Issue) -> Boolean
  fun getFilteredIssues(): List<Issue>
  fun registerUpdateListener(listener: Runnable)
}

class DesignToolsIssueProvider(project: Project) : DesignerCommonIssueProvider<Any> {

  private val fileEditorManager: FileEditorManager

  private val sourceToIssueMap = mutableMapOf<Any, List<Issue>>()

  private val listeners = mutableListOf<Runnable>()
  private val messageBusConnection = project.messageBus.connect()

  private var _filter: (Issue) -> Boolean = { true }
  override var filter: (Issue) -> Boolean
    get() = _filter
    set(value) { _filter = value }

  init {
    Disposer.register(project, this)
    fileEditorManager = FileEditorManager.getInstance(project)
    messageBusConnection.subscribe(IssueProviderListener.TOPIC, object : IssueProviderListener {
      override fun issueUpdated(source: Any, issues: List<Issue>) {
        val filteredIssues = filterSelectedOrNoFileIssues(issues)
        if (filteredIssues.isEmpty()) {
          sourceToIssueMap.remove(source)
        }
        else {
          sourceToIssueMap[source] = filteredIssues
        }
        listeners.forEach { it.run() }
      }
    })

    // This is a workaround to remove the issues if [IssueModel.deactivate()] is not called when the selected editor is changed.
    // This may happen in compose preview, which calls [DesignSurface.deactivate()] delayed when editor is changed.
    // TODO(b/222110455): Make [DesignSurface] deactivate the IssueModel when it is no longer visible.
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!source.hasOpenFiles()) {
          cleanUpFileIssues()
        }
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        cleanUpFileIssues()
      }

      /**
       * Remove the file issues if the file is not visible.
       */
      private fun cleanUpFileIssues() {
        for ((source, issues) in sourceToIssueMap.toMap()) {
          val filteredIssues = filterSelectedOrNoFileIssues(issues)
          if (filteredIssues.isEmpty()) {
            sourceToIssueMap.remove(source)
          }
          else {
            sourceToIssueMap[source] = filteredIssues
          }
        }
      }
    })
  }

  /**
   * Remove the file issues if their editors are not selected.
   * If an issue is not from the file (which its [Issue.source.file] is null), it will NOT be removed.
   */
  @Suppress("UnstableApiUsage")
  private fun filterSelectedOrNoFileIssues(issues: List<Issue>): List<Issue> {
    val files = fileEditorManager.selectedEditors.mapNotNull { it.file }
    val ret = mutableListOf<Issue>()
    for (issue in issues) {
      val issueFile = issue.source.file?.let { BackedVirtualFile.getOriginFileIfBacked(it) }
      if (issueFile == null || files.contains(issueFile)) {
        ret.add(issue)
      }
    }
    return ret
  }

  override fun getFilteredIssues(): List<Issue> = sourceToIssueMap.values.flatten().filter(filter).toList()

  override fun registerUpdateListener(listener: Runnable) {
    listeners.add(listener)
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    listeners.clear()
  }
}
