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
package com.android.build.attribution.ui

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.intellij.build.BuildContentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl

/**
 * This class is responsible for creating, opening and properly disposing of Build attribution UI.
 */
class BuildAttributionUiManager(
  private val project: Project
) {

  private val buildContentManager: BuildContentManager by lazy {
    ServiceManager.getService(project, BuildContentManager::class.java)
  }

  private var buildContent: Content? = null
  private var buildAttributionTreeView: BuildAttributionTreeView? = null

  private lateinit var reportUiData: BuildAttributionReportUiData


  fun showNewReport(reportUiData: BuildAttributionReportUiData) {
    this.reportUiData = reportUiData
    ApplicationManager.getApplication().invokeLater { updateReportUI() }
  }

  @UiThread
  private fun updateReportUI() {
    createNewView()
    buildContent?.takeIf { it.isValid }?.apply { replaceContentView() } ?: createNewTab()
  }

  private fun createNewView() {
    buildAttributionTreeView?.let { treeView -> Disposer.dispose(treeView) }
    buildAttributionTreeView = BuildAttributionTreeView(reportUiData, TaskIssueReporter(reportUiData, project))
      .also { newView -> newView.setInitialSelection() }
  }

  private fun Content.replaceContentView() {
    buildAttributionTreeView?.let { view ->
      component = view.component
      Disposer.register(this, view)
    }
  }

  private fun createNewTab() {
    buildAttributionTreeView?.let { view ->
      buildContent = ContentImpl(view.component, "Build Speed", true).also { content ->
        Disposer.register(project, content)
        Disposer.register(content, view)
        // When tab is getting closed (and disposed) we want to release the reference on the view.
        Disposer.register(content, Disposable { clearUi() })
        buildContentManager.addContent(content)
      }
    }
  }

  private fun clearUi() {
    buildAttributionTreeView = null
    buildContent = null
  }

  fun openTab() {
    ApplicationManager.getApplication().invokeLater {
      if (buildContent?.isValid != true) {
        createNewView()
        createNewTab()
      }
      buildContentManager.setSelectedContent(buildContent, true, true, false) {}
    }
  }
}