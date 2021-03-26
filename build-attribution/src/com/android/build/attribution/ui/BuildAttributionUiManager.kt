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
import com.android.build.attribution.ui.controllers.BuildAnalyzerViewController
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TaskIssueReporterImpl
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.panels.htmlTextLabelWithFixedLines
import com.android.build.attribution.ui.view.BuildAnalyzerComboBoxView
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
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
  fun showNewReport(reportUiData: BuildAttributionReportUiData, buildSessionId: String)
  fun onBuildFailure(buildSessionId: String)
  fun openTab(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource)
  fun requestOpenTabWhenDataReady(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource)
  fun hasDataToShow(): Boolean
  val stateReporter: BuildAttributionStateReporter

  companion object {
    fun getInstance(project: Project): BuildAttributionUiManager {
      return project.getService(BuildAttributionUiManager::class.java)
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
  var buildAttributionView: ComponentContainer? = null

  @VisibleForTesting
  var buildContent: Content? = null

  override val stateReporter: BuildAttributionStateReporterImpl by lazy { BuildAttributionStateReporterImpl(project, this) }

  private var contentManager: ContentManager? = null

  private var openRequest: OpenRequest = OpenRequest.NO_REQUEST

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
    invokeLaterIfNotDisposed {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
      stateReporter.setStateDataExist()
    }
  }

  override fun onBuildFailure(buildSessionId: String) {
    this.reportUiData = failedBuildReportData()
    invokeLaterIfNotDisposed {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
    }
  }

  private fun failedBuildReportData(): BuildAttributionReportUiData {
    return object : BuildAttributionReportUiData {
      override val successfulBuild: Boolean
        get() = false
      override val buildSummary: BuildSummary
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val criticalPathTasks: CriticalPathTasksUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val criticalPathPlugins: CriticalPathPluginsUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val issues: List<TaskIssuesGroup>
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val configurationTime: ConfigurationUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val annotationProcessors: AnnotationProcessorsReport
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
    }
  }

  @UiThread
  private fun updateReportUI() {
    val content = buildContent
    if (content != null && content.isValid) {
      // Tab is open, replace UI for both successful and failed builds.
      createNewView()
      content.replaceContentView()
    }
    else if (reportUiData.successfulBuild) {
      // Tab is closed, create new tab only in successful build case.
      createNewView()
      createNewTab()
    }
  }

  private fun createNewView() {
    buildAttributionView?.let { existingView -> Disposer.dispose(existingView) }
    if (reportUiData.successfulBuild) {
      val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)
      buildAttributionView = if (StudioFlags.NEW_BUILD_ANALYZER_UI_NAVIGATION_ENABLED.get()) {
        NewViewComponentContainer(reportUiData, issueReporter, uiAnalytics)
      }
      else {
        BuildAttributionTreeView(reportUiData, issueReporter, uiAnalytics)
          .also { newView -> newView.setInitialSelection() }
      }
    }
    else {
      buildAttributionView = BuildFailureViewComponentContainer()
    }
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
    if (hasDataToShow()) {
      invokeLaterIfNotDisposed {
        if (buildContent?.isValid != true) {
          createNewView()
          createNewTab()
        }
        uiAnalytics.registerOpenEventSource(eventSource)
        contentManager!!.setSelectedContent(buildContent!!, true, true)
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.BUILD)!!.show {}
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

  override fun hasDataToShow(): Boolean = this::reportUiData.isInitialized && this.reportUiData.successfulBuild

  override fun dispose() = cleanUp()

  private fun invokeLaterIfNotDisposed(runnable: () -> Unit) = ApplicationManager.getApplication().invokeLater(
    runnable,
    { project.isDisposed }
  )
}

private class NewViewComponentContainer(
  uiData: BuildAttributionReportUiData,
  issueReporter: TaskIssueReporter,
  uiAnalytics: BuildAttributionUiAnalytics
) : ComponentContainer {
  val view: BuildAnalyzerComboBoxView

  init {
    val model = BuildAnalyzerViewModel(uiData)
    val controller = BuildAnalyzerViewController(model, uiAnalytics, issueReporter)
    view = BuildAnalyzerComboBoxView(model, controller)
  }

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

private data class OpenRequest(
  val shouldOpen: Boolean,
  val eventSource: BuildAttributionUiAnalytics.TabOpenEventSource
) {
  companion object {
    val NO_REQUEST = OpenRequest(false, BuildAttributionUiAnalytics.TabOpenEventSource.TAB_HEADER)
    fun requestFrom(eventSource: BuildAttributionUiAnalytics.TabOpenEventSource) = OpenRequest(true, eventSource)
  }
}