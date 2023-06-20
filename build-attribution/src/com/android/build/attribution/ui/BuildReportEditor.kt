/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporterImpl
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.intellij.diff.util.FileEditorBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.text.DateFormatUtil
import javax.swing.Icon
import javax.swing.JComponent

class BuildReportFileType private constructor() : FileType {
  override fun getName(): String = "BuildReport"
  override fun getDescription(): String = "Build report"
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon = AllIcons.Toolwindows.ToolWindowBuild
  override fun isBinary(): Boolean = true
  override fun isReadOnly(): Boolean = true

  companion object {
    val INSTANCE = BuildReportFileType()
  }
}


class BuildReportFile(
  val buildResults: BuildAnalysisResults,
  val project: Project) : LightVirtualFile(
    "Build report: ${DateFormatUtil.formatDateTime((buildResults.getBuildFinishedTimestamp()))}",
    BuildReportFileType.INSTANCE,
    ""
  ) {
  init {
    isWritable = false
  }
}

class BuildReportEditor(private val buildReportFile: BuildReportFile) : FileEditorBase() {
  private val buildResults = buildReportFile.buildResults
  private val project = buildReportFile.project
  private val reportUiData = BuildAttributionReportBuilder(buildResults).build()
  private var newViewComponentContainer: NewViewComponentContainer? = null
  private val uiAnalytics = BuildAttributionUiAnalytics(
    project,
    uiSizeProvider = { newViewComponentContainer?.component?.size },
  )
  private val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)

  init {
    uiAnalytics.newReportSessionId(buildResults.getBuildSessionID())
    newViewComponentContainer = NewViewComponentContainer(reportUiData, project, issueReporter, uiAnalytics).also { view ->
      Disposer.register(this, view)
    }
  }

  override fun getComponent(): JComponent = newViewComponentContainer!!.component
  override fun getPreferredFocusedComponent(): JComponent = newViewComponentContainer!!.preferredFocusableComponent
  override fun getName(): String = "Build Report Editor"
  override fun getFile() = buildReportFile
}

class BuildReportEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is BuildReportFile
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return BuildReportEditor(file as BuildReportFile)
  }

  override fun getEditorTypeId(): String = "BuildReportEditor"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}