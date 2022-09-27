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

import com.android.annotations.concurrency.GuardedBy
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
  var viewOptionFilter: Filter
  fun getFilteredIssues(): List<Issue>
  fun registerUpdateListener(listener: Runnable)

  fun interface Filter : (Issue) -> Boolean
}

object EmptyFilter : DesignerCommonIssueProvider.Filter {
  override fun invoke(p1: Issue): Boolean = true
}

object NotSuppressedFilter : DesignerCommonIssueProvider.Filter {
  override fun invoke(issue: Issue): Boolean {
    return !((issue as? VisualLintRenderIssue)?.isSuppressed() ?: false)
  }
}

class SelectedEditorFilter(project: Project) : DesignerCommonIssueProvider.Filter {
  private val editorManager: FileEditorManager = FileEditorManager.getInstance(project)
  override fun invoke(issue: Issue): Boolean {
    return if (issue is VisualLintRenderIssue) {
      val files = issue.source.models.map { it.virtualFile.name }.distinct()
      editorManager.selectedEditor?.file?.let { files.contains(it.name) } ?: false
    }
    else {
      @Suppress("UnstableApiUsage")
      val issuedFile = issue.source.file?.let { BackedVirtualFile.getOriginFileIfBacked(it) }
      editorManager.selectedEditor?.file?.let { it == issuedFile } ?: false
    }
  }
}

operator fun DesignerCommonIssueProvider.Filter.plus(filter: DesignerCommonIssueProvider.Filter) : DesignerCommonIssueProvider.Filter {
  return DesignerCommonIssueProvider.Filter { issue -> this@plus.invoke(issue) && filter.invoke(issue) }
}

/**
 * [issueFilter] is the filter that always applies for when calling [getFilteredIssues].
 */
class DesignToolsIssueProvider(project: Project, private val issueFilter: DesignerCommonIssueProvider.Filter)
  : DesignerCommonIssueProvider<Any> {
  private val mapLock = Any()
  @GuardedBy("mapLock")
  private val sourceToIssueMap = mutableMapOf<Any, List<Issue>>()

  private val listeners = mutableListOf<Runnable>()
  private val messageBusConnection = project.messageBus.connect()

  private var _viewOptionFilter: DesignerCommonIssueProvider.Filter = EmptyFilter
  override var viewOptionFilter: DesignerCommonIssueProvider.Filter
    get() = _viewOptionFilter
    set(value) {
      _viewOptionFilter = value
      listeners.forEach { it.run() }
    }

  init {
    Disposer.register(project, this)
    messageBusConnection.subscribe(IssueProviderListener.TOPIC, IssueProviderListener { source, issues ->
      synchronized(mapLock) {
        if (issues.isEmpty()) {
          sourceToIssueMap.remove(source)
        }
        else {
          sourceToIssueMap[source] = issues
        }
      }
      listeners.forEach { it.run() }
    })

    // This is a workaround to make issue panel update the tree, because the displaying issues need to be updated after switching the file.
    // TODO(b/222110455): Make [DesignSurface] deactivate the IssueModel when it is no longer visible.
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        listeners.forEach { it.run() }
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        listeners.forEach { it.run() }
      }
    })
  }

  override fun getFilteredIssues(): List<Issue> {
    val values = synchronized(mapLock) { sourceToIssueMap.values.toList() }
    return values.flatten().filter(issueFilter).filter(viewOptionFilter)
  }

  override fun registerUpdateListener(listener: Runnable) {
    listeners.add(listener)
  }

  override fun dispose() {
    messageBusConnection.disconnect()
    listeners.clear()
  }
}
