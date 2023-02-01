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
import com.android.build.attribution.analyzers.createProjectConfigurationOperationDescriptor
import com.android.build.attribution.analyzers.downloadFailureStub
import com.android.build.attribution.analyzers.downloadFinishEventStub
import com.android.build.attribution.analyzers.downloadOperationDescriptorStub
import com.android.build.attribution.analyzers.downloadStartEventStub
import com.android.build.attribution.analyzers.downloadSuccessStub
import com.android.build.attribution.analyzers.failureStub
import com.android.build.attribution.analyzers.url1
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.gradle.tooling.events.ProgressEvent
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test

/**
 * This class tests how gradle TAPI Download events are converted and populate the UI model that is rendered on build output.
 */
class DownloadEventsProcessorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val projectConfigurationDescriptor = createProjectConfigurationOperationDescriptor(":")

  @Test
  fun testSuccessfulDownloadEventsProcessed() {
    doTestReceivedEvents(
      gradleProgressEvents = listOf(
        downloadStartEventStub(downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor)),
        downloadFinishEventStub(
          downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor),
          downloadSuccessStub(0, 100, 10000) // time: 100, totalRepoTime: 100, totalRepoBytes: 10000
        )
      ),
      expectedModelUpdates = listOf(
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE),
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE, true, 10000, 100)
      )
    )
  }

  @Test
  fun testFailedDownloadEventsProcessed() {
    doTestReceivedEvents(
      gradleProgressEvents = listOf(
        downloadStartEventStub(downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor)),
        downloadFinishEventStub(
          downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor),
          downloadFailureStub(0, 100, 10000, listOf(failureStub("Failed request 1", listOf(failureStub("Caused by 1", emptyList())))))
        )
      ),
      expectedModelUpdates = listOf(
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE),
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE, true, 10000, 100, "Failed request 1\nCaused by 1\n"),
      )
    )
  }

  @Test
  fun testNotFoundDownloadEventsProcessed() {
    doTestReceivedEvents(
      gradleProgressEvents = listOf(
        downloadStartEventStub(downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor)),
        downloadFinishEventStub(
          downloadOperationDescriptorStub(url = url1,parent = projectConfigurationDescriptor),
          downloadSuccessStub(0, 100, 0) // time: 100, totalRepoTime: 100, totalRepoBytes: 10000
        )
      ),
      expectedModelUpdates = listOf(
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE),
        DownloadRequestItem(DownloadRequestKey(0, url1), GOOGLE, true, 0, 100, "Not Found"),
      )
    )
  }

  private fun doTestReceivedEvents(
    gradleProgressEvents: List<ProgressEvent>,
    expectedModelUpdates: List<DownloadRequestItem>
  ) {
    val buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    val modelUpdates = mutableListOf<Pair<ExternalSystemTaskId, DownloadRequestItem>>()
    projectRule.project.messageBus.connect(projectRule.testRootDisposable)
      .subscribe(DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC, object : DownloadsInfoUIModelNotifier.Listener {
        override fun updateDownloadRequest(taskId: ExternalSystemTaskId, downloadRequest: DownloadRequestItem) {
          modelUpdates.add(taskId to downloadRequest)
        }
      })
    val eventsProcessor = DownloadsAnalyzer.DownloadEventsProcessor(
      statsAccumulator = null,
      downloadsUiModelNotifier = DownloadsInfoUIModelNotifier(projectRule.project, buildId)
    )
    gradleProgressEvents.forEach {
      eventsProcessor.receiveEvent(it)
    }
    Truth.assertThat(modelUpdates).isEqualTo(expectedModelUpdates.map { buildId to it })
  }
}