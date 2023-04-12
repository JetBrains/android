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

import com.android.build.attribution.BUILD_ANALYZER_NOTIFICATION_GROUP_ID
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.url1
import com.android.build.attribution.analyzers.url2
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.base.Ticker
import com.google.common.truth.Truth
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class LongDownloadsNotifierTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val virtualTimeScheduler = VirtualTimeScheduler()
  private val manualTicker = object : Ticker() {
    override fun read(): Long = virtualTimeScheduler.currentTimeNanos
  }
  private var notificationCounter = 0
  private lateinit var buildId: ExternalSystemTaskId
  private lateinit var buildDisposable: CheckedDisposable

  @Before
  fun setUp() {
    buildId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, projectRule.project)
    buildDisposable = Disposer.newCheckedDisposable("DownloadsInfoPresentableEventTest_buildDisposable")
    Disposer.register(projectRule.testRootDisposable, buildDisposable)
    projectRule.project.messageBus.connect(projectRule.testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == BUILD_ANALYZER_NOTIFICATION_GROUP_ID) {
          notificationCounter++
        }
      }
    })
  }

  @Test
  fun testNoNotificationsWithNoDownloads() {
    LongDownloadsNotifier(buildId, projectRule.project, buildDisposable, 0, virtualTimeScheduler, manualTicker)
    virtualTimeScheduler.advanceBy(15, TimeUnit.SECONDS)
    virtualTimeScheduler.advanceBy(15, TimeUnit.SECONDS)
    virtualTimeScheduler.advanceBy(15, TimeUnit.SECONDS)
    virtualTimeScheduler.advanceBy(15, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)
  }

  @Test
  fun testNotificationShownWithOneLongRunningDownload() {
    val notifier = LongDownloadsNotifier(buildId, projectRule.project, buildDisposable, 0, virtualTimeScheduler, manualTicker)
    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )

    virtualTimeScheduler.advanceBy(29, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)
    virtualTimeScheduler.advanceBy(2, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(1)
  }

  @Test
  fun testNotificationShownWithTwoParallelLongRunningDownloads() {
    val notifier = LongDownloadsNotifier(buildId, projectRule.project, buildDisposable, 0, virtualTimeScheduler, manualTicker)
    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )
    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url2), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )

    virtualTimeScheduler.advanceBy(29, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)
    virtualTimeScheduler.advanceBy(2, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(1)
  }

  @Test
  fun testNotificationNotShownWithCompletedDownload() {
    val notifier = LongDownloadsNotifier(buildId, projectRule.project, buildDisposable, 0, virtualTimeScheduler, manualTicker)
    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )
    virtualTimeScheduler.advanceBy(10, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)

    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE, completed = true)
    )

    virtualTimeScheduler.advanceBy(40, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)
  }

  @Test
  fun testNotificationShownWhenSumWallTimeForDownloadsAboveThreshold() {
    val notifier = LongDownloadsNotifier(buildId, projectRule.project, buildDisposable, 0, virtualTimeScheduler, manualTicker)
    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )
    virtualTimeScheduler.advanceBy(10, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)

    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(0, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE, completed = true)
    )

    // 20s passed, 10s used on download1
    virtualTimeScheduler.advanceBy(10, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)

    notifier.updateDownloadRequest(
      DownloadRequestItem(DownloadRequestKey(20000, url1), repository = DownloadsAnalyzer.KnownRepository.GOOGLE)
    )

    // 49s passed, 29s used on downloads
    virtualTimeScheduler.advanceBy(19, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(0)
    // 59s passed, 39s used on downloads, notification should have been sent
    virtualTimeScheduler.advanceBy(10, TimeUnit.SECONDS)
    Truth.assertThat(notificationCounter).isEqualTo(1)
  }
}