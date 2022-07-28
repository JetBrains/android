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

import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
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
        val selectedFiles = fileEditorManager.selectedFiles.toList()
        var changed = false
        if (issues != sourceToIssueMap[source]) {
          changed = true
          sourceToIssueMap[source] = issues
        }
        if (cleanUpFileIssues(selectedFiles) || changed) {
          listeners.forEach { it.run() }
        }
      }
    })

    // This is a workaround to remove the issues if [IssueModel.deactivate()] is not called when the selected editor is changed.
    // This may happen in compose preview, which calls [DesignSurface.deactivate()] delayed when editor is changed.
    // TODO(b/222110455): Make [DesignSurface] deactivate the IssueModel when it is no longer visible.
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!source.hasOpenFiles() && sourceToIssueMap.isNotEmpty()) {
          sourceToIssueMap.clear()
          listeners.forEach { it.run() }
        }
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        if (cleanUpFileIssues(listOfNotNull(event.newFile))) {
          listeners.forEach { it.run() }
        }
      }
    })
  }

  /**
   * Remove the file issues or visual lint issue if the associated file is not visible(selected).
   * Return true if [sourceToIssueMap] is changed, false otherwise.
   */
  private fun cleanUpFileIssues(selectedFiles: List<VirtualFile>): Boolean {
    var changed = false
    for ((source, issues) in sourceToIssueMap.toMap()) {
      val filteredIssues = filterSelectedOrNoFileIssues(issues).filterVisualLintIssues(selectedFiles)
      if (filteredIssues.isEmpty()) {
        sourceToIssueMap.remove(source)
        changed = true
      }
      else {
        if (filteredIssues != issues) {
          sourceToIssueMap[source] = filteredIssues
          changed = true
        }
      }
    }
    return changed

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

  /**
   * Remove the [VisualLintRenderIssue]s which are not related to the given [files]
   */
  private fun List<Issue>.filterVisualLintIssues(files: List<VirtualFile>): List<Issue> {
    return this.filter {
      if (it is VisualLintRenderIssue) {
        it.source.models.map { model -> model.virtualFile }.any { file -> files.contains(file) }
      }
      else true
    }
  }

  override fun getFilteredIssues(): List<Issue> = sourceToIssueMap.toMap().values.flatten()
    .filterNot { (it as? VisualLintRenderIssue)?.isSuppressed() ?: false }
    .filter(filter)
    .toList()

  override fun registerUpdateListener(listener: Runnable) {
    listeners.add(listener)
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    listeners.clear()
  }
}
