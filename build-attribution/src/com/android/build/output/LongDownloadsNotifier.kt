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
import com.google.common.base.Stopwatch
import com.google.common.base.Ticker
import com.intellij.build.BuildContentManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Listens for download events during Sync and tracks time when there  are active downloads ghappening.
 * Once this time is above threshold notification is shown.
 */
class LongDownloadsNotifier(
  private val taskId: ExternalSystemTaskId,
  private val project: Project,
  buildFinishedDisposable: Disposable,
  scheduler: ScheduledExecutorService = EdtExecutorService.getScheduledExecutorInstance(),
  ticker: Ticker = Ticker.systemTicker()
): DownloadsInfoUIModelNotifier.Listener {
  private val runningRequestsSet = mutableSetOf<DownloadRequestKey>()
  private val watch = Stopwatch.createUnstarted(ticker)
  @Volatile private var notified = false
  private val notificationThresholdInSeconds = 30L
  private val notificationRecheckDelayInSeconds = 5L

  init {
    project.messageBus.connect(buildFinishedDisposable).subscribe(DownloadsInfoUIModelNotifier.DOWNLOADS_OUTPUT_TOPIC, this)
    val runnable = object : Runnable {
      override fun run() {
        if (notified) return
        notifyIfTimeElapsed()
      }
    }
    val taskFuture = scheduler.scheduleWithFixedDelay(runnable, notificationThresholdInSeconds, notificationRecheckDelayInSeconds, TimeUnit.SECONDS)
    Disposer.register(buildFinishedDisposable) { taskFuture.cancel(false) }
  }

  override fun updateDownloadRequest(taskId: ExternalSystemTaskId, downloadRequest: DownloadRequestItem) {
    if (this.taskId != taskId) return
    if (notified) return
    if (!downloadRequest.completed) {
      runningRequestsSet.add(downloadRequest.requestKey)
    }
    else {
      runningRequestsSet.remove(downloadRequest.requestKey)
    }
    if (runningRequestsSet.isNotEmpty()) {
      synchronized(watch) { if (!watch.isRunning) watch.start() }
    }
    else {
      synchronized(watch) { if (watch.isRunning) watch.stop() }
    }
    notifyIfTimeElapsed()
  }

  private fun notifyIfTimeElapsed() {
    synchronized(watch) {
      if (!notified && watch.elapsed().seconds >= notificationThresholdInSeconds) {
        NotificationGroupManager.getInstance().getNotificationGroup(BUILD_ANALYZER_NOTIFICATION_GROUP_ID)
          .createNotification("Sync is taking a significant amount of time to download dependencies.", NotificationType.WARNING)
          .addAction(object : AnAction("Open Sync tool window for details") {
            override fun actionPerformed(e: AnActionEvent) {
              // There is no need to select content as it should be automatically selected because Sync is currently running.
              BuildContentManager.getInstance(project).getOrCreateToolWindow().show {}
            }
          }).notify(project)
        notified = true
      }
    }
  }
}