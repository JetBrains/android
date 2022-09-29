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
import com.android.build.attribution.BuildAnalyzerNotificationManager
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildAnalyzerStorageManager.Listener
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.BuildAnalyzerViewController
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TaskIssueReporterImpl
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.view.BuildAnalyzerComboBoxView
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

interface BuildAttributionUiManager : Disposable {
  fun onBuildFailure(buildSessionId: String)
  fun openTab(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource)
  fun showNewReport()
  fun showBuildAnalysisReportById(buildID: String)

  companion object {
    fun getInstance(project: Project): BuildAttributionUiManager {
      return project.getService(BuildAttributionUiManager::class.java)
    }
  }
}

class BuildAnalyzerStorageManagerListenerImpl(val project: Project) : Listener {
  override fun newDataAvailable() {
    BuildAttributionUiManager.getInstance(project).showNewReport()
  }
}

/**
 * This class is responsible for creating, opening and properly disposing of Build attribution UI.
 */
class BuildAttributionUiManagerImpl(
  private val project: Project
) : BuildAttributionUiManager {

  // We are holding reference to view to:
  // 1) dispose it when replaced with a new one
  // 2) get component size for analytics
  // 3) to reinit (re-add) ui components on theme change
  @VisibleForTesting
  var buildAttributionView: ComponentContainer? = null

  // We are holding reference to content to:
  // 1) check tab state
  // 2) track in analytics for tab open/closed
  // 3) to reinit (re-add) ui components on theme change
  // 4) request to open it when asked
  @VisibleForTesting
  var buildContent: Content? = null

  private var contentManager: ContentManager? = null

  private val contentManagerListener = object : ContentManagerListener {
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

  private val uiAnalytics = BuildAttributionUiAnalytics(
    project,
    uiSizeProvider = { buildAttributionView?.component?.size }
  )

  private val notificationManager = BuildAnalyzerNotificationManager(project, uiAnalytics)

  private var latestBuildSuccessful: Boolean = false

  init {
    Disposer.register(project, this)
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC, LafManagerListener { reInitReportUI() })
  }

  override fun showBuildAnalysisReportById(buildID: String) {
    val buildResults = BuildAnalyzerStorageManager.getInstance(project).getHistoricBuildResultByID(buildID)
    val reportFile = BuildReportFile(buildResults, project)
    val fileDescriptor = OpenFileDescriptor(project, reportFile)
    FileEditorManager.getInstance(project).openEditor(fileDescriptor, true)
  }

  override fun showNewReport() {
    invokeLaterIfNotDisposed {
      val buildResults = BuildAnalyzerStorageManager.getInstance(project).getLatestBuildAnalysisResults()
      val reportUiData = BuildAttributionReportBuilder(buildResults).build()
      val buildSessionId = buildResults.getBuildSessionID()
      latestBuildSuccessful = true
      uiAnalytics.newReportSessionId(buildSessionId)

      val content = buildContent
      createAndRegisterNewView {
        val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)
        NewViewComponentContainer(reportUiData, project, issueReporter, uiAnalytics)
      }
      if (content != null && content.isValid) {
        // Tab is open, replace UI for both successful and failed builds.
        content.replaceContentView()
      }
      else {
        createNewTab()
      }

      if (reportUiData.shouldAutoOpenTab()) {
        openTab(BuildAttributionUiAnalytics.TabOpenEventSource.AUTO_OPEN)
      }

      notificationManager.showToolWindowBalloonIfNeeded(reportUiData) {
        openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BALLOON_LINK)
        (buildAttributionView as? NewViewComponentContainer)?.let {
          it.model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
        }
      }
    }
  }

  override fun onBuildFailure(buildSessionId: String) {
    invokeLaterIfNotDisposed {
      latestBuildSuccessful = false
      uiAnalytics.newReportSessionId(buildSessionId)

      val content = buildContent
      if (content != null && content.isValid) {
        // Tab is open, replace UI for both successful and failed builds.
        createAndRegisterNewView { BuildFailureViewComponentContainer() }
        content.replaceContentView()
      }
    }
  }

  @UiThread
  private fun reInitReportUI() {
    val content = buildContent
    if (content != null && content.isValid) {
      buildAttributionView?.let { view ->
        (view as? NewViewComponentContainer)?.reInitUi()
        content.component.removeAll()
        content.component.add(view.component, BorderLayout.CENTER)
      }
    }
  }

  private fun createAndRegisterNewView(viewFactory: () -> ComponentContainer) {
    buildAttributionView?.let { existingView -> Disposer.dispose(existingView) }
    buildAttributionView = viewFactory()
  }

  private fun Content.replaceContentView() {
    buildAttributionView?.let { view ->
      component.removeAll()
      component.add(view.component, BorderLayout.CENTER)
      Disposer.register(this, view)
      uiAnalytics.buildReportReplaced()
    }
  }

  private fun createNewTab() {
    buildAttributionView?.let { view ->
      buildContent = ContentImpl(BorderLayoutPanel(), "Build Analyzer", true).also { content ->
        content.component.add(view.component, BorderLayout.CENTER)
        Disposer.register(this, content)
        Disposer.register(content, view)
        // When tab is getting closed (and disposed) we want to release the reference on the view.
        Disposer.register(content, Disposable { onContentClosed() })
        project.getService(BuildContentManager::class.java).addContent(content)
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
    buildAttributionView = null
    buildContent = null
  }

  override fun openTab(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource) {
    if (BuildAnalyzerStorageManager.getInstance(project).hasData()) {
      invokeLaterIfNotDisposed {
        if (buildContent?.isValid != true) {
          // If tab is closed, we need to retrieve the latest data from the storage and recreate the view.
          if (latestBuildSuccessful) {
            // If latest build was successful, get data from storage and recreate the normal view.
            val buildResults = BuildAnalyzerStorageManager.getInstance(project).getLatestBuildAnalysisResults()
            val reportUiData = BuildAttributionReportBuilder(buildResults).build()
            createAndRegisterNewView {
              val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)
              NewViewComponentContainer(reportUiData, project, issueReporter, uiAnalytics)
            }
          }
          else {
            // If last build failed, we should stick with this empty error view, not change to previous success.
            createAndRegisterNewView { BuildFailureViewComponentContainer() }
          }
          createNewTab()
        }
        uiAnalytics.registerOpenEventSource(eventSource)
        contentManager!!.setSelectedContent(buildContent!!, true, true)
        BuildContentManager.getInstance(project).getOrCreateToolWindow().show {}
      }
    }
  }

  override fun dispose() = cleanUp()

  private fun invokeLaterIfNotDisposed(runnable: () -> Unit) = project.invokeLaterIfNotDisposed(runnable)
}

fun Project.invokeLaterIfNotDisposed(runnable: () -> Unit) = ApplicationManager.getApplication().invokeLater(
  runnable
) { this.isDisposed }

class NewViewComponentContainer(
  uiData: BuildAttributionReportUiData,
  project: Project,
  issueReporter: TaskIssueReporter,
  uiAnalytics: BuildAttributionUiAnalytics
) : ComponentContainer {
  val model = BuildAnalyzerViewModel(uiData, BuildAttributionWarningsFilter.getInstance(project))
  private val controller = BuildAnalyzerViewController(model, project, uiAnalytics, issueReporter)
  private var view = createView()

  fun reInitUi() {
    Disposer.dispose(view)
    view = createView()
  }

  private fun createView() = BuildAnalyzerComboBoxView(model, controller).also { view -> Disposer.register(this, view) }

  override fun getPreferredFocusableComponent(): JComponent = component

  override fun getComponent(): JComponent = view.wholePanel

  override fun dispose() = Unit
}

private class BuildFailureViewComponentContainer : ComponentContainer {

  override fun getPreferredFocusableComponent(): JComponent = component

  override fun getComponent(): JComponent = JPanel().apply {
    layout = BorderLayout(5, 5)
    name = "Build failure empty view"
    border = JBUI.Borders.empty(20)
    add(JLabel(warningIcon()).apply { verticalAlignment = SwingConstants.TOP }, BorderLayout.WEST)
    add(htmlTextLabelWithFixedLines("""
      The Build Analyzer isn't able to analyze your build as the most recent build failed.<br>
      Please address any warnings in the Build Output window and rebuild your project.<br>
    """.trimIndent()), BorderLayout.CENTER)
  }

  override fun dispose() = Unit
}

private fun BuildAttributionReportUiData.shouldAutoOpenTab() : Boolean = when {
  jetifierData.checkJetifierBuild -> true
  else -> false
}
