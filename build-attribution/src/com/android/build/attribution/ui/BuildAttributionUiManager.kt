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
import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.analyzers.ConfigurationCachingCompatibilityProjectResult
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.BuildAnalyzerViewController
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TaskIssueReporterImpl
import com.android.build.attribution.ui.data.AnnotationProcessorsReport
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.BuildSummary
import com.android.build.attribution.ui.data.ConfigurationUiData
import com.android.build.attribution.ui.data.CriticalPathPluginsUiData
import com.android.build.attribution.ui.data.CriticalPathTaskCategoriesUiData
import com.android.build.attribution.ui.data.CriticalPathTasksUiData
import com.android.build.attribution.ui.data.TaskIssuesGroup
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.view.BuildAnalyzerComboBoxView
import com.android.build.attribution.BuildAnalyzerStorageManager.Listener
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.google.common.annotations.VisibleForTesting
import com.intellij.build.BuildContentManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
  fun hasDataToShow(): Boolean
  fun showNewReport()

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
  @VisibleForTesting
  var buildAttributionView: ComponentContainer? = null

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

  private lateinit var reportUiData: BuildAttributionReportUiData

  init {
    Disposer.register(project, this)
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(LafManagerListener.TOPIC, LafManagerListener { reInitReportUI() })
  }

  override fun showNewReport(){
    val buildResults = BuildAnalyzerStorageManager.getInstance(project).getLatestBuildAnalysisResults()
    val reportUiData = BuildAttributionReportBuilder(buildResults).build()
    val buildSessionId = buildResults.getBuildSessionID()
    showNewReport(reportUiData, buildSessionId)
  }

  override fun onBuildFailure(buildSessionId: String) {
    this.reportUiData = failedBuildReportData()
    invokeLaterIfNotDisposed {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
    }
  }

  @VisibleForTesting
  fun showNewReport(reportUiData: BuildAttributionReportUiData, buildSessionId: String) {
    this.reportUiData = reportUiData
    invokeLaterIfNotDisposed {
      uiAnalytics.newReportSessionId(buildSessionId)
      updateReportUI()
      notificationManager.showToolWindowBalloonIfNeeded(reportUiData) {
        openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BALLOON_LINK)
        (buildAttributionView as? NewViewComponentContainer)?.let {
          it.model.selectedData = BuildAnalyzerViewModel.DataSet.WARNINGS
        }
      }
    }
  }

  private fun failedBuildReportData(): BuildAttributionReportUiData {
    return object : BuildAttributionReportUiData {
      override val successfulBuild: Boolean
        get() = false
      override val buildRequestData: GradleBuildInvoker.Request.RequestData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val buildSummary: BuildSummary
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val criticalPathTasks: CriticalPathTasksUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val criticalPathPlugins: CriticalPathPluginsUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val criticalPathTaskCategories: CriticalPathTaskCategoriesUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val issues: List<TaskIssuesGroup>
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val configurationTime: ConfigurationUiData
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val annotationProcessors: AnnotationProcessorsReport
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val confCachingData: ConfigurationCachingCompatibilityProjectResult
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val jetifierData: JetifierUsageAnalyzerResult
        get() = throw UnsupportedOperationException("Shouldn't be called on this object")
      override val downloadsData: DownloadsAnalyzer.Result
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
    if (reportUiData.shouldAutoOpenTab()) {
      openTab(BuildAttributionUiAnalytics.TabOpenEventSource.AUTO_OPEN)
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

  private fun createNewView() {
    buildAttributionView?.let { existingView -> Disposer.dispose(existingView) }
    if (reportUiData.successfulBuild) {
      val issueReporter = TaskIssueReporterImpl(reportUiData, project, uiAnalytics)
      buildAttributionView = NewViewComponentContainer(reportUiData, project, issueReporter, uiAnalytics)
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
        BuildContentManager.getInstance(project).getOrCreateToolWindow().show {}
      }
    }
  }

  override fun hasDataToShow(): Boolean = this::reportUiData.isInitialized && this.reportUiData.successfulBuild

  override fun dispose() = cleanUp()

  private fun invokeLaterIfNotDisposed(runnable: () -> Unit) = project.invokeLaterIfNotDisposed(runnable)
}

fun Project.invokeLaterIfNotDisposed(runnable: () -> Unit) = ApplicationManager.getApplication().invokeLater(
  runnable
) { this.isDisposed }

private class NewViewComponentContainer(
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
  successfulBuild && jetifierData.checkJetifierBuild -> true
  else -> false
}
