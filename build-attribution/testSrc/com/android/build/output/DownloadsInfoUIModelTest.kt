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

import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer.KnownRepository.GOOGLE
import com.android.build.attribution.analyzers.DownloadsAnalyzer.KnownRepository.MAVEN_CENTRAL
import com.android.build.attribution.analyzers.url1
import com.android.build.attribution.analyzers.url2
import com.android.build.attribution.analyzers.url3
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.table.TableView
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.table.DefaultTableCellRenderer

class DownloadsInfoUIModelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var buildId: ExternalSystemTaskId

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
  }

  @Test
  fun testModelInit() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)

    assertThat(model.repositoriesTableModel.items).hasSize(1)
    assertThat(model.repositoriesTableModel.isSortable).isFalse()
    assertThat(model.repositoriesTableModel.columnInfos.map { it.name }).isEqualTo(listOf(
      "Repository",
      "Requests",
      "Data",
      "Time",
      "Avg Speed",
      "Failed Requests",
      "Failed Requests Time"
    ))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 0, 0, 0, 0)

    assertThat(model.requestsTableModel.items).isEmpty()
    assertThat(model.requestsTableModel.isSortable).isFalse()
    assertThat(model.requestsTableModel.columnInfos.map { it.name }).isEqualTo(listOf(
      "Status",
      "File",
      "Time",
      "Size",
      "Avg Speed"
    ))
  }

  @Test
  fun testOneDownload() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository=GOOGLE))
    model.repositoriesTableModel.summaryItem.let {
      assertThat(it.runningNumberOfRequests()).isEqualTo(1)
      assertThat(it.totalNumberOfRequests()).isEqualTo(1)
    }
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = false, receivedBytes = 100, duration = 200))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 200, 100)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(1, 0, 0, 200, 100)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testOneDownloadCompletedRightAway() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testTwoDownloadsSequentially() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)

    val downloadRequestKey1 = DownloadRequestKey(1000, url1)
    val downloadRequestKey2 = DownloadRequestKey(1500, url2)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 1, 0, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(2)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 2, 0, 1000, 4000)
    assertThat(model.requestsTableModel.items).hasSize(2)
  }

  @Test
  fun testTwoDownloadsInParallel() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)

    val downloadRequestKey1 = DownloadRequestKey(1000, url1)
    val downloadRequestKey2 = DownloadRequestKey(1001, url2)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(2, 0, 0, 0, 0)
    assertThat(model.requestsTableModel.items).hasSize(2)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 1, 0, 300, 1000)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 2, 0, 1000, 4000)
    assertThat(model.requestsTableModel.items).hasSize(2)
  }

  @Test
  fun testFailedDownloadCompleted() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)
    val downloadRequestKey = DownloadRequestKey(1000, url1)
    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300, failureMessage = "Failure message"))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 1, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testFiltersOutOtherBuildsData() {
    val newBuildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    val model = DownloadsInfoUIModel(newBuildId, projectRule.testRootDisposable)
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    // Should be empty as it expects updates for different build id.
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 0, 0, 0, 0)
    assertThat(model.requestsTableModel.items).isEmpty()
  }

  @Test
  fun testDataUpdateListeners() {
    val model = DownloadsInfoUIModel(buildId, projectRule.testRootDisposable)
    var notificationCounter = 0

    model.addAndFireDataUpdateListener { notificationCounter++ }
    assertThat(notificationCounter).isEqualTo(1)

    val downloadRequestKey = DownloadRequestKey(1000, url1)
    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE))
    assertThat(notificationCounter).isEqualTo(2)

    updateDownloadRequestViaListener(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    assertThat(notificationCounter).isEqualTo(3)
  }

  private fun updateDownloadRequestViaListener(requestItem: DownloadRequestItem) {
    projectRule.project.messageBus.syncPublisher(DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC).updateDownloadRequest(buildId, requestItem)
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }


  private fun RepositoryTableItem.assertRepositoryItemState(running: Int, completed: Int, failed: Int, durationMs: Long, bytesDownloaded: Long) {
    assertThat(runningNumberOfRequests()).isEqualTo(running)
    assertThat(totalNumberOfRequests()).isEqualTo(completed + running)
    assertThat(numberOfFailed()).isEqualTo(failed)
    assertThat(totalAmountOfTime()).isEqualTo(durationMs)
    assertThat(totalAmountOfData()).isEqualTo(bytesDownloaded)
  }
}

class DownloadsInfoUITableModelsTest {

  @Test
  fun testRequestsTableContentAndFormatting() {
    val model = RequestsTableModel()
    model.items = listOf(
      DownloadRequestItem(DownloadRequestKey(1000, url1), GOOGLE, true, 1234, 1234),
      DownloadRequestItem(DownloadRequestKey(1050, url2), GOOGLE, true, 0, 5678, "Failure message"),
      DownloadRequestItem(DownloadRequestKey(1100, url2), GOOGLE, false, 12, 123)
    )

    val table = TableView(model)
    assertThat(table.rowCount).isEqualTo(3)
    assertThat(table.columnCount).isEqualTo(5)
    val content = (0 until table.rowCount).joinToString(separator = "\n") { row ->
      (0 until table.columnCount).joinToString(separator = " | ") { column ->
        val renderer = table.getCellRenderer(row, column)
        val component = table.prepareRenderer(renderer, row, column)
        if (component is ColoredTableCellRenderer) {
          val icon = when (component.icon) {
            AnimatedIcon.Default.INSTANCE -> "[Load]"
            AllIcons.General.Error -> "[Err]"
            AllIcons.General.Warning -> "[Warn]"
            AllIcons.RunConfigurations.TestPassed -> "[Ok]"
            null -> ""
            else -> "[unexpected icon]"
          }
          val text = component.getCharSequence(false)
          "$icon$text"
        }
        else "Unexpected cell renderer: ${renderer.javaClass}"
      }
    }

    val expectedContent = """
      [Ok]Finished | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.pom | 1 s 234 ms | 1.23 kB | 1 kB/s
      [Warn]Failed | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.jar | 5 s 678 ms | 0 B | 0 B/s
      [Load]Running | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/7.3.0-alpha05/gradle-7.3.0-alpha05.jar | 123 ms | 12 B | 97 B/s
    """.trimIndent()
    assertThat(content).isEqualTo(expectedContent)
  }

  @Test
  fun testRepositoriesTableContentAndFormatting() {
    val model = RepositoriesTableModel()
    listOf(
      DownloadRequestItem(DownloadRequestKey(1000, url1), GOOGLE, true, 1234, 1234),
      DownloadRequestItem(DownloadRequestKey(1050, url2), GOOGLE, true, 0, 5678, "Failure message"),
      DownloadRequestItem(DownloadRequestKey(1100, url2), GOOGLE, false, 12, 123),
      DownloadRequestItem(DownloadRequestKey(1150, url3), MAVEN_CENTRAL, true, 1234, 1234)
    ).forEach { model.update(it) }

    val table = TableView(model)
    assertThat(table.rowCount).isEqualTo(3)
    assertThat(table.columnCount).isEqualTo(7)
    val content = (0 until table.rowCount).joinToString(separator = "\n") { row ->
      (0 until table.columnCount).joinToString(separator = " | ") { column ->
        val renderer = table.getCellRenderer(row, column)
        val component = table.prepareRenderer(renderer, row, column)
        if (component is DefaultTableCellRenderer) {
          val icon = when (component.icon) {
            AnimatedIcon.Default.INSTANCE -> "[Load]"
            AllIcons.General.Error -> "[Err]"
            AllIcons.General.Warning -> "[Warn]"
            AllIcons.RunConfigurations.TestPassed -> "[Ok]"
            null -> ""
            else -> "[unexpected icon]"
          }
          val text = component.text
          "$icon$text"
        }
        else "Unexpected cell renderer: ${renderer.javaClass}"
      }
    }
    val expectedContent = """
      All repositories | 4 (1 running) | 2.48 kB | 8.3s | 299 B/s | 1 | 5.7s
      Google | 3 (1 running) | 1.25 kB | 7.0s | 177 B/s | 1 | 5.7s
      Maven Central | 1 | 1.23 kB | 1.2s | 1 kB/s | 0 | 0.0s
    """.trimIndent()
    assertThat(content).isEqualTo(expectedContent)
  }
}
