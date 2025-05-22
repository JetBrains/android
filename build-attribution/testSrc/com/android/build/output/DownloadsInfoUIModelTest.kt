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
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.ui.table.TableView
import icons.StudioIcons
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DownloadsInfoUIModelTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var buildId: ExternalSystemTaskId
  private lateinit var dataModel: DownloadInfoDataModel
  private lateinit var buildDisposable: Disposable

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    buildDisposable = Disposer.newDisposable(projectRule.testRootDisposable, "Test Build Disposable")
    dataModel = DownloadInfoDataModel(buildDisposable)
  }

  @Test
  fun testModelInit() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }

    assertThat(model.repositoriesTableModel.items).hasSize(0)
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
    assertThat(model.requestsTableModel.isSortable).isTrue()
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
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository=GOOGLE))
    model.repositoriesTableModel.summaryItem.let {
      assertThat(it.runningNumberOfRequests).isEqualTo(1)
      assertThat(it.totalNumberOfRequests).isEqualTo(1)
    }
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = false, receivedBytes = 100, duration = 200))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 200, 100)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(1, 0, 0, 200, 100)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testOneDownloadCompletedRightAway() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items).hasSize(2)
    model.repositoriesTableModel.items[1].assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.repositoriesTableModel.items[1].repository).isEqualTo(GOOGLE)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testTwoDownloadsSequentially() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }

    val downloadRequestKey1 = DownloadRequestKey(1000, url1)
    val downloadRequestKey2 = DownloadRequestKey(1500, url2)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 1, 0, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(2)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 2, 0, 1000, 4000)
    assertThat(model.requestsTableModel.items).hasSize(2)
  }

  @Test
  fun testTwoDownloadsInParallel() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }

    val downloadRequestKey1 = DownloadRequestKey(1000, url1)
    val downloadRequestKey2 = DownloadRequestKey(1001, url2)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(2, 0, 0, 0, 0)
    assertThat(model.requestsTableModel.items).hasSize(2)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey1, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 1, 0, 300, 1000)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey2, repository = GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 2, 0, 1000, 4000)
    assertThat(model.requestsTableModel.items).hasSize(2)
  }

  @Test
  fun testFailedDownloadCompleted() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    val downloadRequestKey = DownloadRequestKey(1000, url1)
    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300, failureMessage = "Failure message"))
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 1, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testDataUpdateListeners() {
    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    var notificationCounter = 0

    model.addAndFireDataUpdateListener { notificationCounter++ }
    assertThat(notificationCounter).isEqualTo(1)

    val downloadRequestKey = DownloadRequestKey(1000, url1)
    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE))
    assertThat(notificationCounter).isEqualTo(2)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    assertThat(notificationCounter).isEqualTo(3)
  }

  @Test
  fun testSecondModelSubscribedLater() {
    val model1 = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    val downloadRequestKey = DownloadRequestKey(100, url1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository=GOOGLE))
    model1.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model1.requestsTableModel.items).hasSize(1)

    val model2 = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    model2.repositoriesTableModel.summaryItem.assertRepositoryItemState(1, 0, 0, 0, 0)
    assertThat(model2.requestsTableModel.items).hasSize(1)

    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    model1.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model1.requestsTableModel.items).hasSize(1)
    model2.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model2.requestsTableModel.items).hasSize(1)
  }

  @Test
  fun testUiModelUnsubscribedBeforeBuildFinished() {
    val model = DownloadsInfoUIModel()
    dataModel.subscribeUiModel(model)
    dataModel.unsubscribeUiModel(model)
    val downloadRequestKey = DownloadRequestKey(100, url1)
    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository=GOOGLE))
    // No updates should happen to this model, it should have been unsubscribed.
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 0, 0, 0, 0)
  }

  @Test
  fun testModelSubscribedAfterBuildFinished() {
    val downloadRequestKey = DownloadRequestKey(100, url1)
    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository=GOOGLE))
    updateDownloadRequest(DownloadRequestItem(downloadRequestKey, repository = GOOGLE, completed = true, receivedBytes = 1000, duration = 300))

    Disposer.dispose(buildDisposable)

    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(0, 1, 0, 300, 1000)
    assertThat(model.requestsTableModel.items).hasSize(1)
  }

  private fun updateDownloadRequest(requestItem: DownloadRequestItem) {
    dataModel.onNewItemUpdate(requestItem)
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }

  @Test
  fun testManyUpdatesAtOnce() {

    val model = DownloadsInfoUIModel().apply {
      dataModel.subscribeUiModel(this)
    }
    val repeats = 10000
    (1..repeats).forEach { i ->
      dataModel.onNewItemUpdate(DownloadRequestItem(DownloadRequestKey(1000, url1 + i), GOOGLE, true, 20, 10))
      dataModel.onNewItemUpdate(DownloadRequestItem(DownloadRequestKey(1050, url2 + i), GOOGLE, true, 20, 10, "Failure message"))
      dataModel.onNewItemUpdate(DownloadRequestItem(DownloadRequestKey(1100, url2 + i), GOOGLE, false, 20, 10))
      dataModel.onNewItemUpdate(DownloadRequestItem(DownloadRequestKey(1150, url3 + i), MAVEN_CENTRAL, true, 20, 10))
    }
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    model.repositoriesTableModel.summaryItem.assertRepositoryItemState(repeats, 3 * repeats, repeats, (4 * 10 * repeats).toLong(), (4 * 20 * repeats).toLong())
    assertThat(model.repositoriesTableModel.items).hasSize(3)
  }


  private fun RepositoryTableItem.assertRepositoryItemState(running: Int, completed: Int, failed: Int, durationMs: Long, bytesDownloaded: Long) {
    assertThat(runningNumberOfRequests).isEqualTo(running)
    assertThat(totalNumberOfRequests).isEqualTo(completed + running)
    assertThat(numberOfFailed).isEqualTo(failed)
    assertThat(totalAmountOfTime).isEqualTo(durationMs)
    assertThat(totalAmountOfData).isEqualTo(bytesDownloaded)
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
    val content = printTableContent(table)

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
    model.bulkUpdate(listOf(
      DownloadRequestItem(DownloadRequestKey(1000, url1), GOOGLE, true, 1234, 1234),
      DownloadRequestItem(DownloadRequestKey(1050, url2), GOOGLE, true, 0, 5678, "Failure message"),
      DownloadRequestItem(DownloadRequestKey(1100, url2), GOOGLE, false, 12, 123),
      DownloadRequestItem(DownloadRequestKey(1150, url3), MAVEN_CENTRAL, true, 1234, 1234)
    ))

    val table = TableView(model)
    assertThat(table.rowCount).isEqualTo(3)
    assertThat(table.columnCount).isEqualTo(7)
    val content = printTableContent(table)

    val expectedContent = """
      Total | 4 (1 running) | 2.48 kB | 8 s 269 ms | 299 B/s | 1 | 5 s 678 ms
      Google | 3 (1 running) | 1.25 kB | 7 s 35 ms | 177 B/s | 1 | 5 s 678 ms
      Maven Central | 1 | 1.23 kB | 1 s 234 ms | 1 kB/s | 0 | 0 ms
    """.trimIndent()
    assertThat(content).isEqualTo(expectedContent)
  }

  private fun printTableContent(table: TableView<*>): String {
    val content = (0 until table.rowCount).joinToString(separator = "\n") { row ->
      (0 until table.columnCount).joinToString(separator = " | ") { column ->
        val renderer = table.getCellRenderer(row, column)
        val component = table.prepareRenderer(renderer, row, column)
        if (component is ColoredTableCellRenderer) {
          val icon = when (component.icon) {
            AnimatedIcon.Default.INSTANCE -> "[Load]"
            StudioIcons.Common.WARNING_INLINE -> "[Warn]"
            StudioIcons.Common.SUCCESS_INLINE -> "[Ok]"
            null -> ""
            else -> "[unexpected icon]"
          }
          val text = component.getCharSequence(false)
          "$icon$text"
        }
        else "Unexpected cell renderer: ${renderer.javaClass}"
      }
    }
    return content
  }
}
