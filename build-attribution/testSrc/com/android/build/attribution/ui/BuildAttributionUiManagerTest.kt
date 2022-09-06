/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.data.BuildAttributionReportUiData
import com.android.build.attribution.ui.data.builder.AbstractBuildAttributionReportBuilderTest
import com.android.build.attribution.ui.data.builder.BuildAttributionReportBuilder
import com.android.testutils.MockitoKt.mock
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildContentManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.impl.ContentImpl
import org.jetbrains.android.AndroidTestCase
import java.util.UUID
import javax.swing.JPanel


class BuildAttributionUiManagerTest : AndroidTestCase() {

  private lateinit var windowManager: ToolWindowManager

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private lateinit var buildAttributionUiManager: BuildAttributionUiManagerImpl
  private lateinit var reportUiData: BuildAttributionReportUiData
  private lateinit var buildSessionId: String

  override fun setUp() {
    super.setUp()
    UsageTracker.setWriterForTest(tracker)
    windowManager = ToolWindowHeadlessManagerImpl(project)
    registerProjectService(ToolWindowManager::class.java, windowManager)
    registerProjectService(BuildContentManager::class.java, BuildContentManagerImpl(project))

    // Add a fake build tab
    project.getService(BuildContentManager::class.java).addContent(
      ContentImpl(JPanel(), BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get(), true)
    )

    buildAttributionUiManager = BuildAttributionUiManagerImpl(project)
    reportUiData = BuildAttributionReportBuilder(AbstractBuildAttributionReportBuilderTest.MockResultsProvider()).build()
    buildSessionId = UUID.randomUUID().toString()
  }

  override fun tearDown() {
    UsageTracker.cleanAfterTesting()
    super.tearDown()
  }

  fun testShowNewReport() {
    setNewReportData(reportUiData, buildSessionId)

    verifyBuildAnalyzerTabExist()
    verifyBuildAnalyzerTabNotSelected()

    // Verify state
    Truth.assertThat(buildAttributionUiManager.buildAttributionView).isNotNull()
    Truth.assertThat(buildAttributionUiManager.buildContent).isNotNull()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED
    ).inOrder()
  }

  fun testOnBuildFailureWhenTabClosed() {
    sendOnBuildFailure(buildSessionId)

    verifyBuildAnalyzerTabNotExist()

    // Verify state
    Truth.assertThat(buildAttributionUiManager.buildAttributionView).isNull()
    Truth.assertThat(buildAttributionUiManager.buildContent).isNull()

    // Verify no metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    Truth.assertThat(buildAttributionEvents).isEmpty()
  }


  fun testShowNewReportAndOpenWithLink() {
    setNewReportData(reportUiData, buildSessionId)
    openBuildAnalyzerTabFromAction()

    verifyBuildAnalyzerTabExist()
    verifyBuildAnalyzerTabSelected()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK,
    ).inOrder()
  }

  fun testShowNewReportAndOpenWithTabClick() {
    setNewReportData(reportUiData, buildSessionId)
    selectBuildAnalyzerTab()

    verifyBuildAnalyzerTabExist()
    verifyBuildAnalyzerTabSelected()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK,
    ).inOrder()
  }

  fun testContentTabClosed() {
    setNewReportData(reportUiData, buildSessionId)
    // Get the reference to check the state later
    val buildAttributionTreeView = buildAttributionUiManager.buildAttributionView!!
    closeBuildAnalyzerTab()

    verifyBuildAnalyzerTabNotExist()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CLOSED,
    ).inOrder()

    // Verify state cleaned up
    Truth.assertThat(Disposer.isDisposed(buildAttributionTreeView)).isTrue()
    Truth.assertThat(buildAttributionUiManager.buildAttributionView).isNull()
    Truth.assertThat(buildAttributionUiManager.buildContent).isNull()
  }

  fun testReplaceReportWithSecondOne() {
    val buildSessionId1 = UUID.randomUUID().toString()
    val buildSessionId2 = UUID.randomUUID().toString()
    val buildSessionId3 = UUID.randomUUID().toString()

    setNewReportData(reportUiData, buildSessionId1)
    setNewReportData(reportUiData, buildSessionId2)
    closeBuildAnalyzerTab()
    setNewReportData(reportUiData, buildSessionId3)

    verifyBuildAnalyzerTabExist()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId1 to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId1 to BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER,
      buildSessionId2 to BuildAttributionUiEvent.EventType.CONTENT_REPLACED,
      buildSessionId2 to BuildAttributionUiEvent.EventType.TAB_CLOSED,
      buildSessionId2 to BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER,
      buildSessionId3 to BuildAttributionUiEvent.EventType.TAB_CREATED,
    ).inOrder()
  }

  fun testOnBuildFailureWhenOpened() {
    val buildSessionId1 = UUID.randomUUID().toString()
    val buildSessionId2 = UUID.randomUUID().toString()

    setNewReportData(reportUiData, buildSessionId1)
    sendOnBuildFailure(buildSessionId2)

    verifyBuildAnalyzerTabExist()

    // Verify state
    Truth.assertThat(buildAttributionUiManager.buildAttributionView).isNotNull()
    Truth.assertThat(buildAttributionUiManager.buildAttributionView?.component?.name).isEqualTo("Build failure empty view")
    Truth.assertThat(buildAttributionUiManager.buildContent).isNotNull()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId1 to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId1 to BuildAttributionUiEvent.EventType.USAGE_SESSION_OVER,
      buildSessionId2 to BuildAttributionUiEvent.EventType.CONTENT_REPLACED,
    ).inOrder()
  }

  fun testReportTabSelectedAndUnselected() {
    setNewReportData(reportUiData, buildSessionId)

    verifyBuildAnalyzerTabExist()

    selectBuildAnalyzerTab()
    selectBuildTab()
    selectBuildAnalyzerTab()

    verifyBuildAnalyzerTabSelected()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_HIDDEN,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK
    ).inOrder()
  }

  fun testBuildOutputLinkClickAfterTabUnselected() {
    setNewReportData(reportUiData, buildSessionId)

    verifyBuildAnalyzerTabExist()

    selectBuildAnalyzerTab()
    selectBuildTab()
    openBuildAnalyzerTabFromAction()

    verifyBuildAnalyzerTabSelected()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_TAB_CLICK,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_HIDDEN,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
    ).inOrder()
  }

  fun testBuildOutputLinkClickAfterTabClosed() {
    setNewReportData(reportUiData, buildSessionId)

    closeBuildAnalyzerTab()
    openBuildAnalyzerTabFromAction()

    verifyBuildAnalyzerTabExist()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CLOSED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED,
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_OPENED_WITH_BUILD_OUTPUT_LINK
    ).inOrder()

    // Verify manager state
    Truth.assertThat(buildAttributionUiManager.buildAttributionView).isNotNull()
    Truth.assertThat(Disposer.isDisposed(buildAttributionUiManager.buildAttributionView!!)).isFalse()
    Truth.assertThat(buildAttributionUiManager.buildContent).isNotNull()
  }

  fun testProjectCloseBeforeAnyBuildFinished() {
    // Regression test for b/147449711.
    // Calling disposeRootDisposable() before would result in an NullPointerException exception being thrown in metrics sending logic
    // because it tried to send a session end event even though no data have been shown yet (thus no session exist to be ended).
    disposeRootDisposable()
  }

  fun testAutoOpenedOnCheckJetifierBuilds() {
    val buildAnalysisResult = object : AbstractBuildAttributionReportBuilderTest.MockResultsProvider() {
      override fun getJetifierUsageResult(): JetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(
        JetifierCanBeRemoved,
        lastCheckJetifierBuildTimestamp = 0,
        checkJetifierBuild = true
      )
    }
    val reportUiData = BuildAttributionReportBuilder(buildAnalysisResult).build()
    setNewReportData(reportUiData, buildSessionId)

    verifyBuildAnalyzerTabExist()
    verifyBuildAnalyzerTabSelected()

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
      .map { it.studioEvent.buildAttributionUiEvent.run { buildAttributionReportSessionId to eventType }}

    Truth.assertThat(buildAttributionEvents).containsExactly(
      buildSessionId to BuildAttributionUiEvent.EventType.TAB_CREATED
    ).inOrder()
  }

  private fun openBuildAnalyzerTabFromAction() {
    buildAttributionUiManager.openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BUILD_OUTPUT_LINK)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun setNewReportData(reportUiData: BuildAttributionReportUiData, buildSessionId: String) {
    buildAttributionUiManager.showNewReport(reportUiData, buildSessionId)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun sendOnBuildFailure(buildSessionId: String) {
    buildAttributionUiManager.onBuildFailure(buildSessionId)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private fun selectBuildTab() {
    contentManager().let { it.setSelectedContent(it.findContent(BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get())) }
  }

  private fun selectBuildAnalyzerTab() = contentManager().let { it.setSelectedContent(it.findContent("Build Analyzer")) }

  private fun closeBuildAnalyzerTab() {
    contentManager().removeContent(contentManager().findContent("Build Analyzer"), true)
  }

  private fun verifyBuildAnalyzerTabExist() = Truth.assertThat(contentManager().findContent("Build Analyzer")).isNotNull()

  private fun verifyBuildAnalyzerTabNotExist() = Truth.assertThat(contentManager().findContent("Build Analyzer")).isNull()

  private fun verifyBuildAnalyzerTabSelected() =
    Truth.assertThat(contentManager().findContent("Build Analyzer").isSelected).isTrue()

  private fun verifyBuildAnalyzerTabNotSelected() =
    Truth.assertThat(contentManager().findContent("Build Analyzer").isSelected).isFalse()

  private fun contentManager() = windowManager.getToolWindow(BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get())!!.contentManager
}
