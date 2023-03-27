/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.output

import com.android.build.attribution.analyzers.DownloadsAnalyzer.KnownRepository.GOOGLE
import com.android.build.attribution.analyzers.DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL
import com.android.build.attribution.analyzers.url1
import com.android.build.attribution.analyzers.url2
import com.android.build.attribution.analyzers.url3
import com.android.testutils.MockitoKt
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.FeatureSurveys
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildOutputDownloadsInfoEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ComponentWithEmptyText
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension

private val gradleVersion_7_2 = GradleVersion.version("7.2")
private val gradleVersion_8_0 = GradleVersion.version("8.0")

class DownloadsInfoPresentableEventTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val buildStartTimestampMs = System.currentTimeMillis()
  private lateinit var buildDisposable: CheckedDisposable
  private lateinit var buildId: ExternalSystemTaskId
  private lateinit var dataModel: DownloadInfoDataModel

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    buildDisposable = Disposer.newCheckedDisposable("DownloadsInfoPresentableEventTest_buildDisposable")
    Disposer.register(projectRule.testRootDisposable, buildDisposable)
    dataModel = DownloadInfoDataModel(buildDisposable)
  }

  @Test
  fun testEventFields() {
    val event = DownloadsInfoPresentableBuildEvent(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_8_0, dataModel)

    assertThat(event.buildId).isSameAs(buildId)
    //Time is not used for this type of event. 0 is a default value for such case.
    assertThat(event.eventTime).isEqualTo(0)
    //This is what is shown as tree item.
    assertThat(event.message).isEqualTo("Download info")
    //Description is text output on execution console. Since we have custom console, we don't have it.
    assertThat(event.description).isNull()
    //Hint is an additional text shown in grey after node name. Is not updatable currently, so do not use.
    assertThat(event.hint).isNull()
    val executionConsole = event.presentationData.executionConsole
      ?.also { Disposer.register(projectRule.testRootDisposable, it) }
    assertThat(executionConsole).isInstanceOf(DownloadsInfoExecutionConsole::class.java)
  }

  @Test
  fun testNodeIcon() {
    val event = DownloadsInfoPresentableBuildEvent(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_8_0, dataModel)

    val icon = event.presentationData.nodeIcon as LayeredIcon
    assertThat(icon.allLayers).isEqualTo(arrayOf(
      AllIcons.Actions.Download,
      AnimatedIcon.Default.INSTANCE
    ))
    fun assertIconStill() {
      assertWithMessage("Download icon layer shown").that(icon.isLayerEnabled(0)).isTrue()
      assertWithMessage("Loading icon layer shown").that(icon.isLayerEnabled(1)).isFalse()
    }

    fun assertIconLoading() {
      assertWithMessage("Download icon layer shown").that(icon.isLayerEnabled(0)).isFalse()
      assertWithMessage("Loading icon layer shown").that(icon.isLayerEnabled(1)).isTrue()
    }

    assertIconStill()

    val downloadProcessKey1 = DownloadRequestKey(1000, url1)
    val downloadProcessKey2 = DownloadRequestKey(1500, url2)

    updateDownloadRequest(DownloadRequestItem(downloadProcessKey1, GOOGLE))
    assertIconLoading()
    updateDownloadRequest(DownloadRequestItem(downloadProcessKey1, GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    assertIconStill()
    updateDownloadRequest(DownloadRequestItem(downloadProcessKey2, GOOGLE))
    assertIconLoading()
    updateDownloadRequest(DownloadRequestItem(downloadProcessKey2, GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    assertIconStill()
  }

  private fun updateDownloadRequest(requestItem: DownloadRequestItem) {
    dataModel.onNewItemUpdate(requestItem)
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }
}

@RunsInEdt
class DownloadsInfoExecutionConsoleTest {
  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val edtRule = EdtRule()

  private val buildStartTimestampMs = System.currentTimeMillis()
  private lateinit var buildId: ExternalSystemTaskId
  private lateinit var executionConsole: DownloadsInfoExecutionConsole
  private lateinit var buildDisposable: CheckedDisposable
  private val featureSurveysMock: FeatureSurveys = MockitoKt.mock()

  private val reposTable: TableView<*>
    get() = TreeWalker(executionConsole.component).descendants().filter { it.name == "repositories table" }.filterIsInstance<TableView<*>>().single()
  private val requestsTable: TableView<*>
    get() = TreeWalker(executionConsole.component).descendants().filter { it.name == "requests table" }.filterIsInstance<TableView<*>>().single()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    buildDisposable = Disposer.newCheckedDisposable("build finished disposable")
    Disposer.register(projectRule.testRootDisposable, buildDisposable)
    executionConsole = DownloadsInfoExecutionConsole(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_8_0, featureSurveysMock)
    Disposer.register(projectRule.testRootDisposable, executionConsole)
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testEmptyUi() {
    assertThat((executionConsole.component as ComponentWithEmptyText).emptyText.text).isEqualTo("No download requests")
    assertWithMessage("None of the component should be visible.")
      .that (executionConsole.component.components.any { it.isVisible }).isFalse()
  }

  @Test
  fun testEmptyUiForOlderGradle() {
    val executionConsole = DownloadsInfoExecutionConsole(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_7_2)
    Disposer.register(projectRule.testRootDisposable, executionConsole)
    assertThat((executionConsole.component as ComponentWithEmptyText).emptyText.text).isEqualTo("Minimal Gradle version providing downloads data is 7.3")
    assertWithMessage("None of the component should be visible.")
      .that (executionConsole.component.components.any { it.isVisible }).isFalse()
  }

  @Test
  fun testEmptyUiForNullGradleVersion() {
    val executionConsole = DownloadsInfoExecutionConsole(buildId, buildDisposable, buildStartTimestampMs, null)

    assertThat((executionConsole.component as ComponentWithEmptyText).emptyText.text).isEqualTo("No download requests")
    assertWithMessage("None of the component should be visible.")
      .that (executionConsole.component.components.any { it.isVisible }).isFalse()
  }

  @Test
  fun testUiUpdated() {
    val downloadProcessKey = DownloadRequestKey(1000, url1)
    executionConsole.uiModel.updateDownloadRequest(DownloadRequestItem(downloadProcessKey, GOOGLE))

    assertWithMessage("Components should become visible on data arrival.")
      .that(executionConsole.component.components.all { it.isVisible }).isTrue()
    assertThat(reposTable.rowCount).isEqualTo(2)
    assertThat(requestsTable.rowCount).isEqualTo(1)
  }

  @Test
  fun testRequestsUpdatedOnRepoSelection() {
    val downloadProcessKey1 = DownloadRequestKey(1000, url1)
    val downloadProcessKey2 = DownloadRequestKey(1150, url3)
    executionConsole.uiModel.updateDownloadRequest(DownloadRequestItem(downloadProcessKey1, GOOGLE))
    executionConsole.uiModel.updateDownloadRequest(DownloadRequestItem(downloadProcessKey2, MAVEN_CENTRAL))

    assertThat(reposTable.rowCount).isEqualTo(3)
    assertThat(requestsTable.rowCount).isEqualTo(2)

    reposTable.setRowSelectionInterval(1,1)

    assertThat(reposTable.rowCount).isEqualTo(3)
    assertThat(requestsTable.rowCount).isEqualTo(1)

    val interactions = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_DOWNLOADS_INFO_USER_INTERACTION }
      .map { use -> use.studioEvent.buildOutputDownloadsInfoEvent.interaction }
    assertThat(interactions).isEqualTo(listOf(BuildOutputDownloadsInfoEvent.Interaction.SELECT_REPOSITORY_ROW))
  }

  @Test
  fun testUIBecomingVisible() {
    val page = executionConsole.component
    // page is not initially visible
    page.isVisible = false
    page.size = Dimension(600, 400)

    val ui = FakeUi(page, createFakeWindow = true)
    page.isVisible = true
    ui.layoutAndDispatchEvents()

    val interactions = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_DOWNLOADS_INFO_USER_INTERACTION }
      .map { use -> use.studioEvent.buildOutputDownloadsInfoEvent.interaction }
    assertThat(interactions).isEqualTo(listOf(BuildOutputDownloadsInfoEvent.Interaction.OPEN_DOWNLOADS_INFO_UI))
    Mockito.verify(featureSurveysMock, Mockito.times(1)).triggerSurveyByName("DOWNLOAD_INFO_VIEW_SURVEY")
  }

  @Test
  fun testSurveyNotTriggeredForBuild() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, projectRule.project)
    executionConsole = DownloadsInfoExecutionConsole(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_8_0, featureSurveysMock)
    Disposer.register(projectRule.testRootDisposable, executionConsole)
    val page = executionConsole.component
    // page is not initially visible
    page.isVisible = false
    page.size = Dimension(600, 400)

    val ui = FakeUi(page, createFakeWindow = true)
    page.isVisible = true
    ui.layoutAndDispatchEvents()

    val interactions = tracker.usages
      .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_OUTPUT_DOWNLOADS_INFO_USER_INTERACTION }
      .map { use -> use.studioEvent.buildOutputDownloadsInfoEvent.interaction }
    assertThat(interactions).isEqualTo(listOf(BuildOutputDownloadsInfoEvent.Interaction.OPEN_DOWNLOADS_INFO_UI))
    Mockito.verifyNoInteractions(featureSurveysMock)
  }

  @Test
  fun testSortToggle() {
    val downloadProcess1 = DownloadRequestItem(DownloadRequestKey(1000, url1), GOOGLE, completed = true, duration = 200)
    val downloadProcess2 = DownloadRequestItem(DownloadRequestKey(2000, url2), GOOGLE, completed = true, duration = 100)
    val downloadProcess3 = DownloadRequestItem(DownloadRequestKey(3000, url3), MAVEN_CENTRAL, completed = true, duration = 300)
    executionConsole.uiModel.updateDownloadRequest(downloadProcess1)
    executionConsole.uiModel.updateDownloadRequest(downloadProcess2)
    executionConsole.uiModel.updateDownloadRequest(downloadProcess3)

    // Test three-state-sorting based on duration column.
    // First toggle should sort by duration in ascending order
    requestsTable.rowSorter.toggleSortOrder(2)
    assertThat((0..2).map { requestsTable.getRow(it) }).isEqualTo(listOf(downloadProcess2, downloadProcess1, downloadProcess3))
    // Second toggle should sort by duration in descending order
    requestsTable.rowSorter.toggleSortOrder(2)
    assertThat((0..2).map { requestsTable.getRow(it) }).isEqualTo(listOf(downloadProcess3, downloadProcess1, downloadProcess2))
    // Third toggle should reset sorting to original order
    requestsTable.rowSorter.toggleSortOrder(2)
    assertThat((0..2).map { requestsTable.getRow(it) }).isEqualTo(listOf(downloadProcess1, downloadProcess2, downloadProcess3))
  }

  @Test
  fun testBuildFinishedBeforeUiDisposed() {
    var executionConsoleDisposed = false
    Disposer.register(executionConsole) { executionConsoleDisposed = true }
    Disposer.dispose(buildDisposable)
    assertThat(executionConsoleDisposed).isFalse()
    Disposer.dispose(executionConsole)
  }

  @Test
  fun testBuildFinishedAfterUiDisposed() {
    Disposer.dispose(executionConsole)
    assertThat(buildDisposable.isDisposed).isFalse()
    Disposer.dispose(buildDisposable)
  }

  @Test
  fun testBuildFinishedBeforeUiCreated() {
    Disposer.dispose(buildDisposable)
    val lateExecutionConsole = DownloadsInfoExecutionConsole(buildId, buildDisposable, buildStartTimestampMs, gradleVersion_8_0)
    Disposer.register(projectRule.testRootDisposable, lateExecutionConsole)
  }
}