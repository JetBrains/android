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
import com.android.build.attribution.analyzers.url1
import com.android.build.attribution.analyzers.url2
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.table.TableView
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DownloadsInfoPresentableEventTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var buildId: ExternalSystemTaskId

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
  }


  @Test
  fun testEventFields() {

    val event = DownloadsInfoPresentableEvent(buildId, projectRule.testRootDisposable)

    assertThat(event.buildId).isSameAs(buildId)
    //Time is not used for this type of event. 0 is a default value for such case.
    assertThat(event.eventTime).isEqualTo(0)
    //This is what is shown as tree item.
    assertThat(event.message).isEqualTo("Downloads info")
    //Description is text output on execution console. Since we have custom console, we don't have it.
    assertThat(event.description).isNull()
    //Hint is an additional text shown in grey after node name. Is not updatable currently, so do not use.
    assertThat(event.hint).isNull()
    assertThat(event.presentationData.executionConsole).isInstanceOf(DownloadsInfoExecutionConsole::class.java)
  }

  @Test
  fun testNodeIcon() {

    val event = DownloadsInfoPresentableEvent(buildId, projectRule.testRootDisposable)
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

    updateDownloadRequestViaListener(DownloadRequestItem(downloadProcessKey1, GOOGLE))
    assertIconLoading()
    updateDownloadRequestViaListener(DownloadRequestItem(downloadProcessKey1, GOOGLE, completed = true, receivedBytes = 1000, duration = 300))
    assertIconStill()
    updateDownloadRequestViaListener(DownloadRequestItem(downloadProcessKey2, GOOGLE))
    assertIconLoading()
    updateDownloadRequestViaListener(DownloadRequestItem(downloadProcessKey2, GOOGLE, completed = true, receivedBytes = 3000, duration = 700))
    assertIconStill()
  }

  private fun updateDownloadRequestViaListener(requestItem: DownloadRequestItem) {
    projectRule.project.messageBus.syncPublisher(DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC).updateDownloadRequest(buildId, requestItem)
    runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
  }
}

class DownloadsInfoExecutionConsoleTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var buildId: ExternalSystemTaskId
  private lateinit var executionConsole: DownloadsInfoExecutionConsole
  private val reposTable: TableView<*>
    get() = TreeWalker(executionConsole.component).descendants().filter { it.name == "repositories table" }.filterIsInstance<TableView<*>>().single()
  private val requestsTable: TableView<*>
    get() = TreeWalker(executionConsole.component).descendants().filter { it.name == "requests table" }.filterIsInstance<TableView<*>>().single()

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    executionConsole = DownloadsInfoExecutionConsole(buildId, Disposer.newDisposable("build finished disposable"))
    Disposer.register(projectRule.testRootDisposable, executionConsole)
  }


  @Test
  fun testEmptyUi() {
    assertThat(reposTable.rowCount).isEqualTo(1)
    assertThat(requestsTable.rowCount).isEqualTo(0)
    assertThat(requestsTable.emptyText.text).isEqualTo("No download requests")
  }

  @Test
  fun testUiUpdated() {
    val downloadProcessKey = DownloadRequestKey(1000, url1)
    executionConsole.uiModel.updateDownloadRequest(DownloadRequestItem(downloadProcessKey, GOOGLE))

    assertThat(reposTable.rowCount).isEqualTo(2)
    assertThat(requestsTable.rowCount).isEqualTo(1)
    assertThat(requestsTable.emptyText.text).isEqualTo("No download requests")
  }
}