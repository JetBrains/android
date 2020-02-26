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
import com.android.build.attribution.BuildAttributionStateReporter
import com.android.build.attribution.BuildAttributionStateReporterImpl
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporterImpl
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout

interface BuildAttributionUiManager : Disposable {
  fun showNewReport(reportUiData: BuildAttributionReportUiData, buildSessionId: String)
  fun openTab(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource)
  fun requestOpenTabWhenDataReady(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource)
  fun hasDataToShow(): Boolean
  val stateReporter: BuildAttributionStateReporter

  companion object {
    fun getInstance(project: Project): BuildAttributionUiManager {
      return ServiceManager.getService(project, BuildAttributionUiManager::class.java)
    }
  }
}

/**
 * This class is responsible for creating, opening and properly disposing of Build attribution UI.
 */
class BuildAttributionUiManagerImpl(
  private val project: Project
) : BuildAttributionUiManager {


  @VisibleForTesting
  var buildAttributionTreeView: BuildAttributionTreeView? = null

  @VisibleForTesting
  var buildContent: Content? = null

  override val stateReporter: BuildAttributionStateReporterImpl by lazy { BuildAttributionStateReporterImpl(project, this) }

  private var contentManager: ContentManager? = null

  private var openRequest: OpenRequest = OpenRequest.NO_REQUEST

  private val contentManagerListener = object : ContentManagerAdapter() {
    override fun selectionChanged(event: ContentManagerEvent) {
      if (event.content !== buildContent) {
        return
      }
      if (event.operation == ContentManagerEvent.ContentOperation.add) {
        uiAnalytics.tabOpened()
      }
      else if (event.operation == ContentManagerEvent.ContentOperation.remove) {
        uiAnalytics.tabHidden()
      }
    }
  }

  private val uiAnalytics = BuildAttributionUiAnalytics(project)

  private lateinit var reportUiData: BuildAttributionReportUiData

  init {
    Disposer.register(project, this)
    project.messageBus.connect(this).subscribe(
      BuildAttributionStateReporter.FEATURE_STATE_TOPIC,
      object : BuildAttributionStateReporter.Notifier {
        override fun stateUpdated(newState: BuildAttributionStateReporter.State) {
          if (newState == BuildAttributionStateReporter.State.REPORT_DATA_READY && openRequest.shouldOpen) {
            openTab(openRequest.eventSource)
            openRequest = OpenRequest.NO_REQUEST
          }
        }
      })
  }

  override fun showNewReport(reportUiData: BuildAttributionReportUiData, buildSessionId: String) {
    this.reportUiData = reportUiData
    ApplicationManager.getApplication().invokeLater {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
      stateReporter.setStateDataExist()
    }
  }

  @UiThread
  private fun updateReportUI() {
    createNewView()
    buildContent?.takeIf { it.isValid }?.apply { replaceContentView() } ?: run {
      createNewTab()
    }
  }

  private fun createNewView() {
    buildAttributionTreeView?.let { treeView -> Disposer.dispose(treeView) }
    val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)
    buildAttributionTreeView = BuildAttributionTreeView(reportUiData, issueReporter, uiAnalytics)
      .also { newView -> newView.setInitialSelection() }
  }

  private fun Content.replaceContentView() {
    buildAttributionTreeView?.let { view ->
      component.removeAll()
      component.add(view.component, BorderLayout.CENTER)
      Disposer.register(this, view)
      uiAnalytics.buildReportReplaced()
    }
  }

  private fun createNewTab() {
    buildAttributionTreeView?.let { view ->
      buildContent = ContentImpl(BorderLayoutPanel(), "Build Analyzer", true).also { content ->
        content.component.add(view.component, BorderLayout.CENTER)
        Disposer.register(this, content)
        Disposer.register(content, view)
        // When tab is getting closed (and disposed) we want to release the reference on the view.
        Disposer.register(content, Disposable { onContentClosed() })
        ServiceManager.getService(project, BuildContentManager::class.java).addContent(content)
        uiAnalytics.tabCreated()
        contentManager = content.manager
        contentManager?.addContentManagerListener(contentManagerListener)
      }
    }
  }

  private fun onContentClosed() {
    uiAnalytics.tabClosed()
    cleanUp()
  }

  private fun cleanUp() {
    contentManager?.removeContentManagerListener(contentManagerListener)
    contentManager = null
    buildAttributionTreeView = null
    buildContent = null
  }

  override fun openTab(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource) {
    if (hasDataToShow()) {
      ApplicationManager.getApplication().invokeLater {
        if (buildContent?.isValid != true) {
          createNewView()
          createNewTab()
        }
        uiAnalytics.registerOpenEventSource(eventSource)
        contentManager!!.setSelectedContent(buildContent!!, true, true)
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.BUILD).show {}
      }
    }
  }

  override fun requestOpenTabWhenDataReady(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource) {
    if (stateReporter.currentState() == BuildAttributionStateReporter.State.REPORT_DATA_READY) {
      openTab(eventSource)
    }
    else {
      openRequest = OpenRequest.requestFrom(eventSource)
    }
  }

  override fun hasDataToShow(): Boolean = this::reportUiData.isInitialized

  override fun dispose() = cleanUp()
}

private data class OpenRequest(
  val shouldOpen: Boolean,
  val eventSource: BuildAttributionUiAnalytics.TabOpenEventSource
) {
  companion object {
    val NO_REQUEST = OpenRequest(false, BuildAttributionUiAnalytics.TabOpenEventSource.TAB_HEADER)
    fun requestFrom(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource) = OpenRequest(true, eventSource)
  }
}